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

import io.fabric8.maven.docker.access.*;
import io.fabric8.maven.docker.config.*;
import io.fabric8.maven.docker.log.LogDispatcher;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.service.*;
import io.fabric8.maven.docker.util.StartOrderResolver;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.codehaus.plexus.util.StringUtils;


/**
 * Goal for creating and starting a docker container. This goal evaluates the image configuration
 *
 * @author roland
 */
@Mojo(name = "start", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
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

    private static final class StartedContainer {
        public final ImageConfiguration imageConfig;
        public final String containerId;

        private StartedContainer(ImageConfiguration imageConfig, String containerId) {
            this.imageConfig = imageConfig;
            this.containerId = containerId;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void executeInternal(final ServiceHub hub) throws DockerAccessException,
                                                                          ExecException,
                                                                          MojoExecutionException {
        if (skipRun) {
            return;
        }
        getPluginContext().put(CONTEXT_KEY_START_CALLED, true);

        this.follow = followLogs();

        QueryService queryService = hub.getQueryService();
        final RunService runService = hub.getRunService();

        PortMapping.PropertyWriteHelper portMappingPropertyWriteHelper = new PortMapping.PropertyWriteHelper(portPropertyFile);

        boolean success = false;

        final ExecutorService executorService = getExecutorService();
        final ExecutorCompletionService<StartedContainer> containerStartupService = new ExecutorCompletionService<>(executorService);

        try {
            // All aliases which are provided in the image configuration:
            final Set<String> imageAliases = new HashSet<>();
            // Remember all aliases which has been started
            final Set<String> startedContainerAliases = new HashSet<>();

            // All images to to start
            Queue<ImageConfiguration> imagesWaitingToStart = prepareStart(hub, queryService, runService, imageAliases);

            // Queue of images to start as containers
            final Queue<ImageConfiguration> imagesStarting = new ArrayDeque<>();

            // Prepare the shutdown hook for stopping containers if we are going to follow them.  Add the hook before starting any
            // of the containers so that partial or aborted starts will behave the same as fully-successful ones.
            if (follow) {
                runService.addShutdownHookForStoppingContainers(keepContainer, removeVolumes, autoCreateCustomNetworks);
            }

            // Loop until every image has been started and the start of all images has been completed
            while (!hasBeenAllImagesStarted(imagesWaitingToStart, imagesStarting)) {

                final List<ImageConfiguration> imagesReadyToStart =
                    getImagesWhoseDependenciesHasStarted(imagesWaitingToStart, startedContainerAliases, imageAliases);

                for (final ImageConfiguration image : imagesReadyToStart) {

                    startImage(image, hub, containerStartupService, portMappingPropertyWriteHelper);

                    // Move from waiting to starting status
                    imagesStarting.add(image);
                    imagesWaitingToStart.remove(image);
                    
                    if (!startParallel) {
                        waitForStartedContainer(hub, containerStartupService, startedContainerAliases, imagesStarting);
                    }
                }

                if (startParallel) {
                    waitForStartedContainer(hub, containerStartupService, startedContainerAliases, imagesStarting);
                }
            }

            portMappingPropertyWriteHelper.write();

            if (follow) {
                wait();
            }

            success = true;
        } catch (InterruptedException e) {
            log.warn("Interrupted");
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("interrupted", e);
        } catch (IOException e) {
            throw new MojoExecutionException("I/O Error", e);
        } finally {
            shutdownExecutorService(executorService);

            // Rollback if not all could be started
            if (!success) {
                log.error("Error occurred during container startup, shutting down...");
                runService.stopStartedContainers(keepContainer, removeVolumes, autoCreateCustomNetworks, getPomLabel());
            }
        }
    }

    private void waitForStartedContainer(final ServiceHub hub,
            final ExecutorCompletionService<StartedContainer> containerStartupService,
            final Set<String> startedContainerAliases, final Queue<ImageConfiguration> imagesStarting)
            throws InterruptedException, DockerAccessException, IOException, ExecException {
        final Future<StartedContainer> startedContainerFuture = containerStartupService.take();
        try {
            final StartedContainer startedContainer = startedContainerFuture.get();
            final ImageConfiguration imageConfig = startedContainer.imageConfig;

            updateAliasesSet(startedContainerAliases, imageConfig.getAlias());
            exposeContainerProps(hub.getQueryService(), startedContainer);

            // All done with this image
            imagesStarting.remove(imageConfig);
        } catch (ExecutionException e) {
            rethrowCause(e);
        }
    }

    protected Boolean followLogs() {
        return Boolean.valueOf(System.getProperty("docker.follow", "false"));
    }

    // Check if we are done
    private boolean hasBeenAllImagesStarted(Queue<ImageConfiguration> imagesWaitingToStart, Queue<ImageConfiguration> imagesStarting) {
        return imagesWaitingToStart.isEmpty() && imagesStarting.isEmpty();
    }

    private void shutdownExecutorService(ExecutorService executorService) {
        if (!executorService.isShutdown()) {
            executorService.shutdown();
            try {
                executorService.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.warn("ExecutorService did not shutdown normally.");
                executorService.shutdownNow();
            }
        }
    }

    private void rethrowCause(ExecutionException e) throws IOException, InterruptedException, ExecException {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        } else if (cause instanceof IOException) {
            throw (IOException) cause;
        } else if (cause instanceof ExecException) {
            throw (ExecException) cause;
        } else if (cause instanceof InterruptedException) {
                throw (InterruptedException) cause;
        } else {
            throw new RuntimeException("Start-Job failed with unexpected exception: " + e.getCause().getMessage(),
                                       e.getCause());
        }
    }

    private void updateAliasesSet(Set<String> aliasesSet, String alias) {
        // Add the alias to the set only when it is set. When it's
        // not set it cant be used in the dependency resolution anyway, so we are ignoring
        // it hence.
        if (alias != null) {
            aliasesSet.add(alias);
        }
    }

    private void startImage(final ImageConfiguration image,
                            final ServiceHub hub,
                            final ExecutorCompletionService<StartedContainer> startingContainers,
                            final PortMapping.PropertyWriteHelper portMappingPropertyWriteHelper) {

        final RunService runService = hub.getRunService();
        final Properties projProperties = project.getProperties();
        final RunImageConfiguration runConfig = image.getRunConfiguration();
        final PortMapping portMapping = runService.createPortMapping(runConfig, projProperties);
        final LogDispatcher dispatcher = getLogDispatcher(hub);

        startingContainers.submit(new Callable<StartedContainer>() {
            @Override
            public StartedContainer call() throws Exception {
                final String containerId = runService.createAndStartContainer(image, portMapping, getPomLabel(), projProperties, project.getBasedir());

                // Update port-mapping writer
                portMappingPropertyWriteHelper.add(portMapping, runConfig.getPortPropertyFile());

                if (showLogs(image)) {
                    dispatcher.trackContainerLog(containerId,
                            serviceHubFactory.getLogOutputSpecFactory().createSpec(containerId, image));
                }

                // Wait if requested
                hub.getWaitService().wait(image, projProperties, containerId);
                WaitConfiguration waitConfig = runConfig.getWaitConfiguration();
                if (waitConfig != null && waitConfig.getExec() != null && waitConfig.getExec().getPostStart() != null) {
                    try {
                        runService.execInContainer(containerId, waitConfig.getExec().getPostStart(), image);
                    } catch (ExecException exp) {
                        if (waitConfig.getExec().isBreakOnError()) {
                            throw exp;
                        } else {
                            log.warn("Cannot run postStart: %s", exp.getMessage());
                        }
                    }
                }

                return new StartedContainer(image, containerId);
            }
        });
    }

    // Pick out all images who can be started right now because all their dependencies has been started
    private List<ImageConfiguration> getImagesWhoseDependenciesHasStarted(Queue<ImageConfiguration> imagesRemaining,
                                                                          Set<String> containersStarted,
                                                                          Set<String> aliases) {
        final List<ImageConfiguration> ret = new ArrayList<>();

        // Check for all images which can be already started
        for (ImageConfiguration imageWaitingToStart : imagesRemaining) {
            List<String> allDependencies = imageWaitingToStart.getDependencies();
            List<String> aliasDependencies = filterOutNonAliases(aliases, allDependencies);
            if (containersStarted.containsAll(aliasDependencies)) {
                ret.add(imageWaitingToStart);
            }
        }
        return ret;
    }

    // Prepare start like creating custom networks, auto pull images, map aliases and return the list of images
    // to start in the correct order
    private Queue<ImageConfiguration> prepareStart(ServiceHub hub, QueryService queryService, RunService runService, Set<String> imageAliases)
        throws DockerAccessException, MojoExecutionException {
        final Queue<ImageConfiguration> imagesWaitingToStart = new ArrayDeque<>();
        for (StartOrderResolver.Resolvable resolvable : runService.getImagesConfigsInOrder(queryService, getResolvedImages())) {
            final ImageConfiguration imageConfig = (ImageConfiguration) resolvable;

            // Still to check: How to work with linking, volumes, etc ....
            //String imageName = new ImageName(imageConfig.getName()).getFullNameWithTag(registry);
            RunImageConfiguration runConfig = imageConfig.getRunConfiguration();

            RegistryService.RegistryConfig registryConfig = getRegistryConfig(pullRegistry);
            ImagePullManager pullManager = getImagePullManager(determinePullPolicy(runConfig), autoPull);

            hub.getRegistryService().pullImageWithPolicy(imageConfig.getName(), pullManager, registryConfig,
                                                         queryService.hasImage(imageConfig.getName()));

            NetworkConfig config = runConfig.getNetworkingConfig();
            if (autoCreateCustomNetworks && config.isCustomNetwork()) {
                runService.createCustomNetworkIfNotExistant(config.getCustomNetwork());
            }
            imagesWaitingToStart.add(imageConfig);
            updateAliasesSet(imageAliases, imageConfig.getAlias());
        }
        return imagesWaitingToStart;
    }

    private String determinePullPolicy(RunImageConfiguration runConfig) {
        return runConfig.getImagePullPolicy() != null ? runConfig.getImagePullPolicy() : imagePullPolicy;
    }

    private List<String> filterOutNonAliases(Set<String> imageAliases, List<String> dependencies) {
        List<String> ret = new ArrayList<>();
        for (String alias : dependencies) {
            if (imageAliases.contains(alias)) {
                ret.add(alias);
            }
        }
        return ret;
    }

    private ExecutorService getExecutorService() {
        final ExecutorService executorService;
        if (startParallel) {
            executorService = Executors.newCachedThreadPool();
        } else {
            executorService = MoreExecutors.newDirectExecutorService();
        }
        return executorService;
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
                return logConfig.isActivated();
            } else {
                // Default is to show logs if "follow" is true
                return follow;
            }
        }
        return false;
    }

    // Expose ports as project properties
    private void exposeContainerProps(QueryService queryService, StartedContainer startedContainer)
        throws DockerAccessException {
        String propKey = getExposedPropertyKeyPart(startedContainer.imageConfig);
        if (StringUtils.isNotEmpty(exposeContainerProps) && StringUtils.isNotEmpty(propKey)) {
            Container container = queryService.getMandatoryContainer(startedContainer.containerId);
            Properties props = project.getProperties();
            String prefix = addDot(exposeContainerProps) + addDot(propKey);
            props.put(prefix + "id", startedContainer.containerId);
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
