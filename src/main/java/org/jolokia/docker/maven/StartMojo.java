package org.jolokia.docker.maven;

/*
 * Copyright 2009-2014 Roland Huss Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by
 * applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.StringUtils;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.access.PortMapping;
import org.jolokia.docker.maven.access.log.LogCallback;
import org.jolokia.docker.maven.access.log.LogGetHandle;
import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.config.LogConfiguration;
import org.jolokia.docker.maven.config.RunImageConfiguration;
import org.jolokia.docker.maven.config.WaitConfiguration;
import org.jolokia.docker.maven.log.LogDispatcher;
import org.jolokia.docker.maven.service.QueryService;
import org.jolokia.docker.maven.service.RunService;
import org.jolokia.docker.maven.util.StartOrderResolver;
import org.jolokia.docker.maven.util.Timestamp;
import org.jolokia.docker.maven.util.WaitUtil;


/**
 * Goal for creating and starting a docker container. This goal evaluates the image configuration
 *
 * @author roland
 * @goal start
 * @phase pre-integration-test
 */
public class StartMojo extends AbstractDockerMojo {

    /**
     * @parameter property = "docker.showLogs"
     */
    private String showLogs;

    /**
     * @parameter property = "docker.follow" default-value = "false"
     */
    protected boolean follow;

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void executeInternal(final DockerAccess dockerAccess) throws DockerAccessException, MojoExecutionException {
        getPluginContext().put(CONTEXT_KEY_START_CALLED, true);

        Properties projProperties = project.getProperties();
        
        QueryService queryService = serviceHub.getQueryService();
        RunService runService = serviceHub.getRunService();

        LogDispatcher dispatcher = getLogDispatcher(dockerAccess);

        PortMapping.PropertyWriteHelper portMappingPropertyWriteHelper = new PortMapping.PropertyWriteHelper(portPropertyFile);
        
        boolean success = false;
        try {
            for (StartOrderResolver.Resolvable resolvable : runService.getImagesConfigsInOrder(queryService, getImages())) {
                final ImageConfiguration imageConfig = (ImageConfiguration) resolvable;

                // Still to check: How to work with linking, volumes, etc ....
                //String imageName = new ImageName(imageConfig.getName()).getFullNameWithTag(registry);

                String imageName = imageConfig.getName();
                checkImageWithAutoPull(dockerAccess, imageName,
                                       getConfiguredRegistry(imageConfig),imageConfig.getBuildConfiguration() == null);

                RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
                PortMapping portMapping = runService.getPortMapping(runConfig, projProperties);

                String containerId = runService.createAndStartContainer(imageConfig, portMapping, projProperties);

                if (showLogs(imageConfig)) {
                    dispatcher.trackContainerLog(containerId, getContainerLogSpec(containerId, imageConfig));
                }

                portMappingPropertyWriteHelper.add(portMapping, runConfig.getPortPropertyFile());

                // Wait if requested
                waitIfRequested(dockerAccess,imageConfig, projProperties, containerId);
                WaitConfiguration waitConfig = runConfig.getWaitConfiguration();
                if (waitConfig != null && waitConfig.getExec() != null && waitConfig.getExec().getPostStart() != null) {
                    runService.execInContainer(containerId, waitConfig.getExec().getPostStart());
                }
            }
            if (follow) {
                runService.addShutdownHookForStoppingContainers(keepContainer,removeVolumes);
                wait();
            }
            
            portMappingPropertyWriteHelper.write();
            success = true;
        } catch (InterruptedException e) {
            log.warn("Interrupted");
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("interrupted", e);
        } catch (IOException e) {
            throw new MojoExecutionException("I/O Error",e);
        } finally {
            if (!success) {
                log.error("Error occurred during container startup, shutting down...");
                runService.stopStartedContainers(keepContainer, removeVolumes);
            }
        }
    }

    // ========================================================================================================

    private void waitIfRequested(DockerAccess docker, ImageConfiguration imageConfig, Properties projectProperties, String containerId) throws MojoExecutionException {
        RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
        WaitConfiguration wait = runConfig.getWaitConfiguration();

        if (wait == null) {
            return;
        }

        ArrayList<WaitUtil.WaitChecker> checkers = new ArrayList<>();
        ArrayList<String> logOut = new ArrayList<>();
        if (wait.getUrl() != null) {
            String waitUrl = StrSubstitutor.replace(wait.getUrl(), projectProperties);
            WaitConfiguration.HttpConfiguration httpConfig = wait.getHttp();
            if (httpConfig != null) {
                checkers.add(new WaitUtil.HttpPingChecker(waitUrl, httpConfig.getMethod(), httpConfig.getStatus()));
            } else {
                checkers.add(new WaitUtil.HttpPingChecker(waitUrl));
            }
            logOut.add("on url " + waitUrl);
        }
        if (wait.getLog() != null) {
            checkers.add(getLogWaitChecker(wait.getLog(), docker, containerId));
            logOut.add("on log out '" + wait.getLog() + "'");
        }

        if (checkers.isEmpty()) {
            if (wait.getTime() > 0) {
                log.info(imageConfig.getDescription() + ": Pausing for " + wait.getTime() + " ms");
                WaitUtil.sleep(wait.getTime());
            }

            return;
        }

        try {
            long waited = WaitUtil.wait(wait.getTime(), checkers);
            log.info(imageConfig.getDescription() + ": Waited " + StringUtils.join(logOut.toArray(), " and ") + " " + waited + " ms");
        } catch (WaitUtil.WaitTimeoutException exp) {
            String desc = imageConfig.getDescription() + ": Timeout after " + exp.getWaited() + " ms while waiting " +
                          StringUtils.join(logOut.toArray(), " and ");
            log.error(desc);
            throw new MojoExecutionException(desc);
        }
    }

    private WaitUtil.WaitChecker getLogWaitChecker(final String logPattern, final DockerAccess docker, final String containerId) {
        return new WaitUtil.WaitChecker() {

            boolean first = true;
            LogGetHandle logHandle;
            boolean detected = false;

            @Override
            public boolean check() {
                if (first) {
                    final Pattern pattern = Pattern.compile(logPattern);
                    logHandle = docker.getLogAsync(containerId, new LogCallback() {
                        @Override
                        public void log(int type, Timestamp timestamp, String txt) throws LogCallback.DoneException {
                            if (pattern.matcher(txt).find()) {
                                detected = true;
                                throw new LogCallback.DoneException();
                            }
                        }

                        @Override
                        public void error(String error) {
                            log.error(error);
                        }
                    });
                    first = false;
                }
                return detected;
            }

            @Override
            public void cleanUp() {
                if (logHandle != null) {
                    logHandle.finish();
                }
            }
        };
    }

    protected boolean showLogs(ImageConfiguration imageConfig) {
        if (showLogs != null) {
            if (showLogs.equalsIgnoreCase("true")) {
                return true;
            } else if (showLogs.equalsIgnoreCase("false")) {
                return false;
            } else {
                return matchesConfiguredImages(showLogs, imageConfig);
            }
        }

        RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
        if (runConfig != null) {
            LogConfiguration logConfig = runConfig.getLog();
            if (logConfig != null) {
                return logConfig.isEnabled();
            } else {
                // Default is to show logs if "follow" is true
                return follow;
            }
        }
        return false;
    }

}
