package io.fabric8.maven.docker;

/*
 * Copyright 2009-2014 Roland Huss Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by
 * applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import com.google.common.util.concurrent.MoreExecutors;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.PortMapping;
import io.fabric8.maven.docker.access.log.LogCallback;
import io.fabric8.maven.docker.access.log.LogGetHandle;
import io.fabric8.maven.docker.config.*;
import io.fabric8.maven.docker.log.LogDispatcher;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.service.QueryService;
import io.fabric8.maven.docker.service.RunService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.*;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.codehaus.plexus.util.StringUtils;


/**
 * Goal for creating and starting a docker container. This goal evaluates the image configuration
 *
 * @author roland
 */
@Mojo(name = "start", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
@Execute(phase = LifecyclePhase.INITIALIZE)
public class StartMojo extends AbstractDockerMojo {

    @Parameter(property = "docker.showLogs")
    private String showLogs;

    @Parameter(property = "docker.pull.registry")
    private String pullRegistry;

    @Parameter(property = "docker.skip.run", defaultValue = "false")
    private boolean skipRun;

    @Parameter(property = "docker.startParallel", defaultValue = "false")
    private boolean startParallel;

    // whether to block during to start. Set it via System property docker.follow
    private boolean follow;

    /**
     * Expose container information like the internal IP as Maven properties which
     * can be reused in the build information. The value of this property is the prefix
     * used for the properties. The default prefix is "docker.container". Only information
     * of images having an alias are exposed and have the format <code>&lt;prefix&gt;.&lt;alias&gt;.&lt;property&gt;</code>.
     * (e.g. <code>docker.container.mycontainer.ip</code>
     * The following properties are currently supported:
     * <ul>
     *   <li><strong>ip</strong> : the container's internal IP address as chosen by Docker</li>
     * </ul>
     *
     * If set to an empty string, no properties are exposed.
     */
    @Parameter(property = "docker.exposeContainerInfo")
    private String exposeContainerProps = "docker.container";

    /**
     * Whether to create the customs networks (user-defined bridge networks) before starting automatically
     */
    @Parameter(property = "docker.autoCreateCustomNetworks", defaultValue = "false")
    protected boolean autoCreateCustomNetworks;

    // property file to write out with port mappings
    @Parameter
    protected String portPropertyFile;

    private static final class StartedContainerImage {
        public final ImageConfiguration imageConfig;
        public final String containerId;

        private StartedContainerImage(ImageConfiguration imageConfig, String containerId) {
            this.imageConfig = imageConfig;
            this.containerId = containerId;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void executeInternal(final ServiceHub hub) throws DockerAccessException,
                                                                          MojoExecutionException {
        if (skipRun) {
            return;
        }
        getPluginContext().put(CONTEXT_KEY_START_CALLED, true);

        final Properties projProperties = project.getProperties();
        this.follow = Boolean.valueOf(System.getProperty("docker.follow", "false"));

        QueryService queryService = hub.getQueryService();
        final RunService runService = hub.getRunService();

        final LogDispatcher dispatcher = getLogDispatcher(hub);
        PortMapping.PropertyWriteHelper portMappingPropertyWriteHelper = new PortMapping.PropertyWriteHelper(portPropertyFile);

        boolean success = false;
        final PomLabel pomLabel = getPomLabel();

        try {

            final Queue<ImageConfiguration> imagesWaitingToStart = new ArrayDeque<>();

            for (StartOrderResolver.Resolvable resolvable : runService.getImagesConfigsInOrder(queryService, getResolvedImages())) {
                final ImageConfiguration imageConfig = (ImageConfiguration) resolvable;

                // Still to check: How to work with linking, volumes, etc ....
                //String imageName = new ImageName(imageConfig.getName()).getFullNameWithTag(registry);

                String imageName = imageConfig.getName();
                checkImageWithAutoPull(hub, imageName,
                        getConfiguredRegistry(imageConfig, pullRegistry), imageConfig.getBuildConfiguration() == null);

                RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
                NetworkConfig config = runConfig.getNetworkingConfig();
                if (autoCreateCustomNetworks && config.isCustomNetwork()) {
                    runService.createCustomNetworkIfNotExistant(config.getCustomNetwork());
                }
                imagesWaitingToStart.add(imageConfig);
            }

            final Set<String> startedContainers = new HashSet<>();

            final ExecutorService executorService;
            if (startParallel) {
                executorService = Executors.newCachedThreadPool();
            } else {
                executorService = MoreExecutors.newDirectExecutorService();
            }
            final ExecutorCompletionService<StartedContainerImage> startingContainers = new ExecutorCompletionService<>(executorService);

            while (!imagesWaitingToStart.isEmpty()) {
                final List<ImageConfiguration> startableImages = new ArrayList<>();

                for (ImageConfiguration imageWaitingToStart : imagesWaitingToStart) {
                    if (startedContainers.containsAll(imageWaitingToStart.getDependencies())) {
                        startableImages.add(imageWaitingToStart);
                    }
                }

                for (final ImageConfiguration startableImage : startableImages) {

                    final RunImageConfiguration runConfig = startableImage.getRunConfiguration();
                    final PortMapping portMapping = runService.getPortMapping(runConfig, projProperties);

                    startingContainers.submit(new Callable<StartedContainerImage>() {
                        @Override
                        public StartedContainerImage call() throws Exception {
                            final String containerId = runService.createAndStartContainer(startableImage, portMapping, pomLabel, projProperties);

                            if (showLogs(startableImage)) {
                                dispatcher.trackContainerLog(containerId,
                                        serviceHubFactory.getLogOutputSpecFactory().createSpec(containerId, startableImage));
                            }

                            // Wait if requested
                            waitIfRequested(hub,startableImage, projProperties, containerId);
                            WaitConfiguration waitConfig = runConfig.getWaitConfiguration();
                            if (waitConfig != null && waitConfig.getExec() != null && waitConfig.getExec().getPostStart() != null) {
                                runService.execInContainer(containerId, waitConfig.getExec().getPostStart(), startableImage);
                            }

                            return new StartedContainerImage(startableImage, containerId);
                        }
                    });
                    imagesWaitingToStart.remove(startableImage);
                }


                final Future<StartedContainerImage> imageStartResult = startingContainers.take();
                try {
                    final StartedContainerImage startedContainerImage = imageStartResult.get();
                    final String containerId = startedContainerImage.containerId;
                    final ImageConfiguration imageConfig = startedContainerImage.imageConfig;
                    final RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
                    final PortMapping portMapping = runService.getPortMapping(runConfig, projProperties);


                    startedContainers.add(startedContainerImage.imageConfig.getAlias());

                    portMappingPropertyWriteHelper.add(portMapping, runConfig.getPortPropertyFile());
                    // Expose container info as properties
                    exposeContainerProps(hub.getQueryService(), containerId, imageConfig);

                } catch (ExecutionException e) {
                    try {
                        if (e.getCause() instanceof RuntimeException) {
                            throw (RuntimeException)e.getCause();
                        } else if (e.getCause() instanceof IOException) {
                            throw (IOException)e.getCause();
                        } else if (e.getCause() instanceof InterruptedException) {
                            throw (InterruptedException)e.getCause();
                        } else {
                            throw new RuntimeException("Start-Job failed with unexpected exception: "+e.getCause().getMessage(), e.getCause());
                        }
                    } finally {
                        executorService.shutdown();
                        try {
                            executorService.awaitTermination(10, TimeUnit.SECONDS);
                        } catch (InterruptedException ie) {
                            log.warn("ExecutorService did not shutdown correctly. Enforcing shutdown now!");
                            executorService.shutdownNow();
                        }
                    }
                }
            }

            if (!executorService.isShutdown()) {
                executorService.shutdown();
                try {
                    executorService.awaitTermination(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    log.warn("ExecutorService did not shutdown normally.");
                }
            }

            if (follow) {
                runService.addShutdownHookForStoppingContainers(keepContainer, removeVolumes, autoCreateCustomNetworks);
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
                runService.stopStartedContainers(keepContainer, removeVolumes, autoCreateCustomNetworks, pomLabel);
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
            checkers.add(getUrlWaitChecker(imageConfig.getDescription(), projectProperties, wait, logOut));
        }

        if (wait.getLog() != null) {
            log.debug("LogWaitChecker: Waiting on %s",wait.getLog());
            checkers.add(getLogWaitChecker(wait.getLog(), hub, containerId));
            logOut.add("on log out '" + wait.getLog() + "'");
        }

        if (wait.getTcp() != null) {
            try {
                Container container = hub.getQueryService().getMandatoryContainer(containerId);
                checkers.add(getTcpWaitChecker(container, imageConfig.getDescription(), projectProperties, wait.getTcp(), logOut));
            } catch (DockerAccessException e) {
                throw new MojoExecutionException("Unable to access container.", e);
            }
        }

        if (checkers.isEmpty()) {
            if (wait.getTime() > 0) {
                log.info("%s: Pausing for %d ms", imageConfig.getDescription(), wait.getTime());
                WaitUtil.sleep(wait.getTime());
            }
            return;
        }

        try {
            long waited = WaitUtil.wait(wait.getTime(), checkers);
            log.info("%s: Waited %s %d ms",imageConfig.getDescription(), StringUtils.join(logOut.toArray(), " and "), waited);
        } catch (WaitUtil.WaitTimeoutException exp) {
            String desc = String.format("%s: Timeout after %d ms while waiting %s",
                                        imageConfig.getDescription(), exp.getWaited(),
                                        StringUtils.join(logOut.toArray(), " and "));
            log.error(desc);
            throw new MojoExecutionException(desc);
        }
    }

    private WaitUtil.WaitChecker getUrlWaitChecker(String imageConfigDesc,
                                                   Properties projectProperties,
                                                   WaitConfiguration wait,
                                                   ArrayList<String> logOut) {
        String waitUrl = StrSubstitutor.replace(wait.getUrl(), projectProperties);
        WaitConfiguration.HttpConfiguration httpConfig = wait.getHttp();
        WaitUtil.HttpPingChecker checker;
        if (httpConfig != null) {
            checker = new WaitUtil.HttpPingChecker(waitUrl, httpConfig.getMethod(), httpConfig.getStatus(), httpConfig.isAllowAllHosts());
            log.info("%s: Waiting on url %s with method %s for status %s.",
                    imageConfigDesc, waitUrl, httpConfig.getMethod(), httpConfig.getStatus());
        } else {
            checker = new WaitUtil.HttpPingChecker(waitUrl);
            log.info("%s: Waiting on url %s.", imageConfigDesc, waitUrl);
        }
        logOut.add("on url " + waitUrl);
        return checker;
    }

    private WaitUtil.WaitChecker getTcpWaitChecker(Container container,
                                                   String imageConfigDesc,
                                                   Properties projectProperties,
                                                   WaitConfiguration.TcpConfiguration tcpConfig,
                                                   ArrayList<String> logOut) throws MojoExecutionException {
        List<Integer> ports = new ArrayList<>();

        List<Integer> portsConfigured = getTcpPorts(tcpConfig);
        String host = getTcpHost(tcpConfig, projectProperties);
        WaitConfiguration.TcpConfigMode mode = getTcpMode(tcpConfig, host, projectProperties);

        if (mode == WaitConfiguration.TcpConfigMode.mapped) {
            for (int port : portsConfigured) {
                Container.PortBinding binding = container.getPortBindings().get(port + "/tcp");
                if (binding == null) {
                    throw new MojoExecutionException(
                        String.format("Cannot watch on port %d, since there is no network binding", port));
                }
                ports.add(binding.getHostPort());
            }
            log.info("%s: Waiting for mapped ports %s on host %s", imageConfigDesc, ports, host);
        } else {
            host = container.getIPAddress();
            ports = portsConfigured;
            log.info("%s: Waiting for ports %s directly on container with IP (%s).",
                     imageConfigDesc, ports, host);
        }
        WaitUtil.TcpPortChecker tcpWaitChecker = new WaitUtil.TcpPortChecker(host, ports);
        logOut.add("on tcp port '" + tcpWaitChecker.getPending() + "'");
        return tcpWaitChecker;
    }

    private List<Integer> getTcpPorts(WaitConfiguration.TcpConfiguration tcpConfig) throws MojoExecutionException {
        List<Integer> portsConfigured = tcpConfig.getPorts();
        if (portsConfigured == null || portsConfigured.size() == 0) {
            throw new MojoExecutionException("TCP wait config given but no ports to wait on");
        }
        return portsConfigured;
    }

    private WaitConfiguration.TcpConfigMode getTcpMode(WaitConfiguration.TcpConfiguration tcpConfig, String host, Properties projectProperties) {
        WaitConfiguration.TcpConfigMode mode = tcpConfig.getMode();
        if (mode == null) {
            return "localhost".equals(host) ? WaitConfiguration.TcpConfigMode.direct : WaitConfiguration.TcpConfigMode.mapped;
        } else {
            return mode;
        }
    }

    private String getTcpHost(WaitConfiguration.TcpConfiguration tcpConfig, Properties projectProperties) {
        String host = tcpConfig.getHost();
        if (host == null) {
            // Host defaults to ${docker.host.address}.
            host = projectProperties.getProperty("docker.host.address");
        }
        return host;
    }

    private WaitUtil.WaitChecker getLogWaitChecker(final String logPattern, final ServiceHub hub, final String  containerId) {
        return new WaitUtil.WaitChecker() {

            boolean first = true;
            LogGetHandle logHandle;
            boolean detected = false;

            @Override
            public synchronized boolean check() {
                if (first) {
                    final Pattern pattern = Pattern.compile(logPattern);
                    log.debug("LogWaitChecker: Pattern to match '%s'",logPattern);
                    DockerAccess docker = hub.getDockerAccess();
                    logHandle = docker.getLogAsync(containerId, new LogCallback() {
                        @Override
                        public void log(int type, Timestamp timestamp, String txt) throws LogCallback.DoneException {
                            log.debug("LogWaitChecker: Tying to match '%s' [Pattern: %s] [thread: %d]",
                                      txt, logPattern, Thread.currentThread().getId());
                            if (pattern.matcher(txt).find()) {
                                detected = true;
                                throw new LogCallback.DoneException();
                            }
                        }

                        @Override
                        public void error(String error) {
                            log.error("%s", error);
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
                return ConfigHelper.matchesConfiguredImages(showLogs, imageConfig);
            }
        }

        RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
        if (runConfig != null) {
            LogConfiguration logConfig = runConfig.getLogConfiguration();
            if (logConfig != null) {
                return logConfig.isEnabled();
            } else {
                // Default is to show logs if "follow" is true
                return follow;
            }
        }
        return false;
    }

    private void exposeContainerProps(QueryService queryService, String containerId, ImageConfiguration image)
        throws DockerAccessException {
        String propKey = getExposedPropertyKeyPart(image);
        if (StringUtils.isNotEmpty(exposeContainerProps) && StringUtils.isNotEmpty(propKey)) {
            Container container = queryService.getMandatoryContainer(containerId);
            Properties props = project.getProperties();
            String prefix = addDot(exposeContainerProps) + addDot(propKey);
            props.put(prefix + "id", containerId);
            String ip = container.getIPAddress();
            if (StringUtils.isNotEmpty(ip)) {
                props.put(prefix + "ip", ip);
            }

            Map<String, String> nets = container.getCustomNetworkIpAddresses();
            if (nets != null) {
                for (Map.Entry<String, String> entry : nets.entrySet()) {
                    props.put(prefix + addDot("net") + addDot(entry.getKey()) + "ip", entry.getValue());
                }
            }
        }
    }

    private String getExposedPropertyKeyPart(ImageConfiguration image) {
        String propKey = image.getRunConfiguration() != null ? image.getRunConfiguration().getExposedPropertyKey() : null;
        if (StringUtils.isEmpty(propKey)) {
            propKey = image.getAlias();
        }
        return propKey;
    }

    private String addDot(String part) {
        return part.endsWith(".") ? part : part + ".";
    }


}
