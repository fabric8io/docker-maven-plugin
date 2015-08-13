package org.jolokia.docker.maven;

/*
 * Copyright 2009-2014 Roland Huss Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by
 * applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.StringUtils;
import org.jolokia.docker.maven.access.*;
import org.jolokia.docker.maven.access.log.LogCallback;
import org.jolokia.docker.maven.access.log.LogGetHandle;
import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.log.LogDispatcher;
import org.jolokia.docker.maven.service.QueryService;
import org.jolokia.docker.maven.service.RunService;
import org.jolokia.docker.maven.util.*;


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

        QueryService queryService = serviceHub.getQueryService();
        RunService runService = serviceHub.getRunService();

        LogDispatcher dispatcher = getLogDispatcher(dockerAccess);

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
                PortMapping portMapping = runService.getPortMapping(runConfig, project.getProperties());

                String containerId = runService.createAndStartContainer(imageConfig, portMapping, project.getProperties());

                if (showLogs(imageConfig)) {
                    dispatcher.trackContainerLog(containerId, getContainerLogSpec(containerId, imageConfig));
                }

                // Set maven properties for dynamically assigned ports.
                updateDynamicPortProperties(dockerAccess, containerId, runConfig, portMapping, project.getProperties());

                // Wait if requested
                waitIfRequested(dockerAccess,imageConfig, project.getProperties(), containerId);
            }
            if (follow) {
                runService.addShutdownHookForStoppingContainers(keepContainer,removeVolumes);
                wait();
            }
            success = true;
        } catch (InterruptedException e) {
            log.warn("Interrupted");
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("interrupted", e);
        } finally {
            if (!success) {
                runService.stopStartedContainers(keepContainer, removeVolumes);
            }
        }
    }

    private void updateDynamicPortProperties(DockerAccess docker, String containerId, RunImageConfiguration runConfig, PortMapping mappedPorts, Properties properties) throws DockerAccessException, MojoExecutionException {
        if (mappedPorts.containsDynamicPorts()) {
            mappedPorts.updateVariablesWithDynamicPorts(docker.queryContainerPortMapping(containerId));
            propagatePortVariables(mappedPorts, runConfig.getPortPropertyFile(),properties);
        }
    }


    // ========================================================================================================


    private void waitIfRequested(DockerAccess docker, ImageConfiguration imageConfig, Properties projectProperties, String containerId) throws MojoExecutionException {
        RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
        WaitConfiguration wait = runConfig.getWaitConfiguration();
        if (wait != null) {
            ArrayList<WaitUtil.WaitChecker> checkers = new ArrayList<>();
            ArrayList<String> logOut = new ArrayList<>();
            if (wait.getUrl() != null) {
                String waitUrl = StrSubstitutor.replace(wait.getUrl(), projectProperties);
                String waitMethod = StrSubstitutor.replace(wait.getMethod(), projectProperties);
                checkers.add(new WaitUtil.HttpPingChecker(waitUrl, waitMethod));
                logOut.add("on url " + waitUrl);
            }
            if (wait.getLog() != null) {
                checkers.add(getLogWaitChecker(wait.getLog(), docker, containerId));
                logOut.add("on log out '" + wait.getLog() + "'");
            }
            try {
                long waited = WaitUtil.wait(wait.getTime(), checkers.toArray(new WaitUtil.WaitChecker[0]));
                log.info(imageConfig.getDescription() + ": Waited " + StringUtils.join(logOut.toArray(), " and ") + " " + waited + " ms");
            } catch (TimeoutException exp) {
                String desc = imageConfig.getDescription() + ": Timeout after " + wait.getTime() + " ms while waiting " +
                              StringUtils.join(logOut.toArray(), " and ");
                log.error(desc);
                throw new MojoExecutionException(desc);
            }
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

    // Store dynamically mapped ports
    private void propagatePortVariables(PortMapping mappedPorts, String portPropertyFile, Properties properties) throws MojoExecutionException {
        Properties props = new Properties();
        Map<String, Integer> dynamicPorts = mappedPorts.getPortVariables();
        for (Map.Entry<String, Integer> entry : dynamicPorts.entrySet()) {
            String var = entry.getKey();
            String val = "" + entry.getValue();
            properties.setProperty(var, val);
            props.setProperty(var, val);
        }

        // However, this can be to late since properties in pom.xml are resolved during the "validate" phase
        // (and we are running later probably in "pre-integration" phase. So, in order to bring the dynamically
        // assigned ports to the integration tests a properties file is written. Not nice, but works. Blame it
        // to maven to not allow late evaluation or any other easy way to inter-plugin communication
        if (portPropertyFile != null) {
            EnvUtil.writePortProperties(props, portPropertyFile);
        }
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
