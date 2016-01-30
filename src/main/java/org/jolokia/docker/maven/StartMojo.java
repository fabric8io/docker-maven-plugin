package org.jolokia.docker.maven;

/*
 * Copyright 2009-2014 Roland Huss Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by
 * applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.StringUtils;
import org.jolokia.docker.maven.access.*;
import org.jolokia.docker.maven.access.log.LogCallback;
import org.jolokia.docker.maven.access.log.LogGetHandle;
import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.log.LogDispatcher;
import org.jolokia.docker.maven.model.Container;
import org.jolokia.docker.maven.service.*;
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

    /** @parameter property = "docker.pull.registry" */
    private String pullRegistry;

    // whether to block during to start. Set it via Sysem property docker.follow
    private boolean follow;

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void executeInternal(final ServiceHub hub) throws DockerAccessException,
                                                                          MojoExecutionException {
        getPluginContext().put(CONTEXT_KEY_START_CALLED, true);

        Properties projProperties = project.getProperties();
        this.follow = Boolean.valueOf(System.getProperty("docker.follow", "false"));

        QueryService queryService = hub.getQueryService();
        RunService runService = hub.getRunService();

        LogDispatcher dispatcher = getLogDispatcher(hub);
        PortMapping.PropertyWriteHelper portMappingPropertyWriteHelper = new PortMapping.PropertyWriteHelper(portPropertyFile);

        boolean success = false;
        try {
            for (StartOrderResolver.Resolvable resolvable : runService.getImagesConfigsInOrder(queryService, getImages())) {
                final ImageConfiguration imageConfig = (ImageConfiguration) resolvable;

                // Still to check: How to work with linking, volumes, etc ....
                //String imageName = new ImageName(imageConfig.getName()).getFullNameWithTag(registry);

                String imageName = imageConfig.getName();
                checkImageWithAutoPull(hub, imageName,
                                       getConfiguredRegistry(imageConfig,pullRegistry),imageConfig.getBuildConfiguration() == null);

                RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
                PortMapping portMapping = runService.getPortMapping(runConfig, projProperties);

                String containerId = runService.createAndStartContainer(imageConfig, portMapping, getPomLabel(), projProperties);

                if (showLogs(imageConfig)) {
                    dispatcher.trackContainerLog(containerId,
                                                 serviceHubFactory.getLogOutputSpecFactory().createSpec(containerId, imageConfig));
                }

                portMappingPropertyWriteHelper.add(portMapping, runConfig.getPortPropertyFile());

                // Wait if requested
                waitIfRequested(hub,imageConfig, projProperties, containerId);
                WaitConfiguration waitConfig = runConfig.getWaitConfiguration();
                if (waitConfig != null && waitConfig.getExec() != null && waitConfig.getExec().getPostStart() != null) {
                    runService.execInContainer(containerId, waitConfig.getExec().getPostStart(), imageConfig);
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

    private void waitIfRequested(ServiceHub hub, ImageConfiguration imageConfig,
                                 Properties projectProperties, String containerId) throws MojoExecutionException {
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
            checkers.add(getLogWaitChecker(wait.getLog(), hub, containerId));
            logOut.add("on log out '" + wait.getLog() + "'");
        }
        if (wait.getTcp() != null) {
            WaitConfiguration.TcpConfiguration tcpConfig = wait.getTcp();
            try {
                Container container = hub.getDockerAccess().inspectContainer(containerId);
                String host = tcpConfig.getHost();
                List<Integer> ports = new ArrayList<>();

                if (host == null) {
                    // Host defaults to ${docker.host.address}.
                    host = projectProperties.getProperty("docker.host.address");
                }

                if ("localhost".equals(host) && container.getIPAddress() != null) {
                    host = container.getIPAddress();
                    ports = tcpConfig.getPorts();
                    log.info(String.format("%s: Waiting for ports %s directly on container with IP (%s).",
                                           imageConfig.getDescription(), ports, host));
                } else {
                    for (int port : tcpConfig.getPorts()) {
                        Container.PortBinding binding = container.getPortBindings().get(port + "/tcp");
                        if (binding == null) {
                            throw new MojoExecutionException(String.format(
                                    "Cannot watch on port %d, since there is no network binding", port
                            ));
                        }
                        ports.add(binding.getHostPort());
                    }
                    log.info(String.format("%s: Waiting for exposed ports %s on remote host (%s), " +
                                           "since they are not directly accessible.",
                                           imageConfig.getDescription(), ports, host));
                }

                WaitUtil.TcpPortChecker tcpWaitChecker = new WaitUtil.TcpPortChecker(host, ports);
                checkers.add(tcpWaitChecker);
                logOut.add("on tcp port '" + tcpWaitChecker.getPending() + "'");

            } catch (DockerAccessException e) {
                throw new MojoExecutionException("Unable to access container.", e);
            }
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

    private WaitUtil.WaitChecker getLogWaitChecker(final String logPattern, final ServiceHub hub, final String
            containerId) {
        return new WaitUtil.WaitChecker() {

            boolean first = true;
            LogGetHandle logHandle;
            boolean detected = false;

            @Override
            public boolean check() {
                if (first) {
                    final Pattern pattern = Pattern.compile(logPattern);
                    DockerAccess docker = hub.getDockerAccess();
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
