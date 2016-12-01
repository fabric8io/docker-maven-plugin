package io.fabric8.maven.docker.service;

/*
 *
 * Copyright 2014 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.fabric8.maven.docker.access.*;
import io.fabric8.maven.docker.config.*;
import io.fabric8.maven.docker.log.LogOutputSpecFactory;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.model.Network;
import io.fabric8.maven.docker.util.*;
import io.fabric8.maven.docker.util.WaitUtil.WaitTimeoutException;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;


/**
 * Service class for helping in running containers.
 *
 * @author roland
 * @since 16/06/15
 */
public class RunService {

    // logger delegated from top
    private final Logger log;

    // Action to be used when doing a shutdown
    final private ContainerTracker tracker;

    // DAO for accessing the docker daemon
    private final DockerAccess docker;

    private final QueryService queryService;

    private final LogOutputSpecFactory logConfig;

    public RunService(final DockerAccess docker,
                      final QueryService queryService,
                      final ContainerTracker tracker,
                      final LogOutputSpecFactory logConfig,
                      final Logger log) {
        this.docker = docker;
        this.queryService = queryService;
        this.tracker = tracker;
        this.log = log;
        this.logConfig = logConfig;
    }

    /**
     * Create and start a Exec container with the given image configuration.
     *
     * @param containerId        container id to run exec command against
     * @param command            command to execute
     * @param imageConfiguration configuration of the container's image
     * @return the exec container id
     * @throws DockerAccessException if access to the docker backend fails
     */
    public String execInContainer(final String containerId, final String command, final ImageConfiguration imageConfiguration)
            throws DockerAccessException {
        final Arguments arguments = new Arguments();
        arguments.setExec(Arrays.asList(EnvUtil.splitOnSpaceWithEscape(command)));
        final String execContainerId = docker.createExecContainer(containerId, arguments);
        docker.startExecContainer(execContainerId, logConfig.createSpec(containerId, imageConfiguration));
        return execContainerId;
    }

    /**
     * Create and start a container with the given image configuration.
     *
     * @param imageConfig image configuration holding the run information and the image name
     * @param portMapping container port mapping
     * @param mavenProps  properties to fill in with dynamically assigned ports
     * @param pomLabel    label to tag the started container with
     * @return the container id
     * @throws DockerAccessException if access to the docker backend fails
     */
    public String createAndStartContainer(final ImageConfiguration imageConfig,
                                          final PortMapping portMapping,
                                          final PomLabel pomLabel,
                                          final Properties mavenProps) throws DockerAccessException {
        final RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
        final String imageName = imageConfig.getName();
        final String containerName = calculateContainerName(imageConfig.getAlias(), runConfig.getNamingStrategy());
        final ContainerCreateConfig config = createContainerConfig(imageName, runConfig, portMapping, pomLabel, mavenProps);

        final String id = docker.createContainer(config, containerName);
        startContainer(imageConfig, id, pomLabel);

        if (portMapping.needsPropertiesUpdate()) {
            updateMappedPortsAndAddresses(id, portMapping);
        }

        return id;
    }

    /**
     * Stop a container immediately by id.
     *
     * @param containerId   the container to stop
     * @param imageConfig   image configuration for this container
     * @param keepContainer whether to keep container or to remove them after stoppings
     * @param removeVolumes whether to remove volumes after stopping
     */
    public void stopContainer(final String containerId,
                              final ImageConfiguration imageConfig,
                              final boolean keepContainer,
                              final boolean removeVolumes)
            throws DockerAccessException {
        final ContainerTracker.ContainerShutdownDescriptor descriptor =
                new ContainerTracker.ContainerShutdownDescriptor(imageConfig, containerId);
        shutdown(descriptor, keepContainer, removeVolumes);
    }

    /**
     * Lookup up whether a certain has been already started and registered. If so, stop it
     *
     * @param containerId   the container to stop
     * @param keepContainer whether to keep container or to remove them after stoppings
     * @param removeVolumes whether to remove volumes after stopping
     * @throws DockerAccessException
     */
    public void stopPreviouslyStartedContainer(final String containerId,
                                               final boolean keepContainer,
                                               final boolean removeVolumes)
            throws DockerAccessException {
        final ContainerTracker.ContainerShutdownDescriptor descriptor = tracker.removeContainer(containerId);
        if (descriptor != null) {
            shutdown(descriptor, keepContainer, removeVolumes);
        }
    }

    /**
     * Stop all registered container
     *
     * @param keepContainer whether to keep container or to remove them after stoppings
     * @param removeVolumes whether to remove volumes after stopping
     * @throws DockerAccessException if during stopping of a container sth fails
     */
    public void stopStartedContainers(final boolean keepContainer,
                                      final boolean removeVolumes,
                                      final boolean removeCustomNetworks,
                                      final PomLabel pomLabel)
            throws DockerAccessException {
        final Set<Network> networksToRemove = new HashSet<>();
        for (final ContainerTracker.ContainerShutdownDescriptor descriptor : tracker.removeShutdownDescriptors(pomLabel)) {
            collectCustomNetworks(networksToRemove, descriptor, removeCustomNetworks);
            shutdown(descriptor, keepContainer, removeVolumes);
        }
        removeCustomNetworks(networksToRemove);
    }

    private void collectCustomNetworks(final Set<Network> networksToRemove, final ContainerTracker.ContainerShutdownDescriptor descriptor, final boolean removeCustomNetworks) throws DockerAccessException {
        final NetworkConfig config = descriptor.getImageConfiguration().getRunConfiguration().getNetworkingConfig();
        if (removeCustomNetworks && config.isCustomNetwork()) {
            networksToRemove.add(queryService.getNetworkByName(config.getCustomNetwork()));
        }
    }

    /**
     * Lookup a container that has been started
     *
     * @param lookup a container by id or alias
     * @return the container id if the container exists, <code>null</code> otherwise.
     */
    public String lookupContainer(final String lookup) {
        return tracker.lookupContainer(lookup);
    }

    /**
     * Get the proper order for images to start
     *
     * @param images list of images for which the order should be created
     * @return list of images in the right startup order
     */
    public List<StartOrderResolver.Resolvable> getImagesConfigsInOrder(final QueryService queryService, final List<ImageConfiguration> images) {
        return StartOrderResolver.resolve(queryService, convertToResolvables(images));
    }

    /**
     * Create port mapping for a specific configuration as it can be used when creating containers
     *
     * @param runConfig  the cun configuration
     * @param properties properties to lookup variables
     * @return the portmapping
     */
    public PortMapping createPortMapping(final RunImageConfiguration runConfig, final Properties properties) {
        try {
            return new PortMapping(runConfig.getPorts(), properties);
        } catch (final IllegalArgumentException exp) {
            throw new IllegalArgumentException("Cannot parse port mapping", exp);
        }
    }

    /**
     * Add a shutdown hook in order to stop all registered containers
     */
    public void addShutdownHookForStoppingContainers(final boolean keepContainer, final boolean removeVolumes, final boolean removeCustomNetworks) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    stopStartedContainers(keepContainer, removeVolumes, removeCustomNetworks, null);
                } catch (final DockerAccessException e) {
                    log.error("Error while stopping containers: %s", e.getMessage());
                }
            }
        });
    }

    private List<StartOrderResolver.Resolvable> convertToResolvables(final List<ImageConfiguration> images) {
        final List<StartOrderResolver.Resolvable> ret = new ArrayList<>();
        for (final ImageConfiguration config : images) {
            if (config.getRunConfiguration().skip()) {
                log.info("%s: Skipped running", config.getDescription());
            } else {
                ret.add(config);
            }
        }
        return ret;
    }

    // visible for testing
    ContainerCreateConfig createContainerConfig(final String imageName, final RunImageConfiguration runConfig, final PortMapping mappedPorts,
                                                final PomLabel pomLabel, final Properties mavenProps)
            throws DockerAccessException {
        try {
            final ContainerCreateConfig config = new ContainerCreateConfig(imageName)
                    .hostname(runConfig.getHostname())
                    .domainname(runConfig.getDomainname())
                    .user(runConfig.getUser())
                    .workingDir(runConfig.getWorkingDir())
                    .entrypoint(runConfig.getEntrypoint())
                    .exposedPorts(mappedPorts.getContainerPorts())
                    .environment(runConfig.getEnvPropertyFile(), runConfig.getEnv(), runConfig.keepEnvs(), mavenProps)
                    .labels(mergeLabels(runConfig.getLabels(), pomLabel))
                    .command(runConfig.getCmd())
                    .hostConfig(createContainerHostConfig(runConfig, mappedPorts));
            final VolumeConfiguration volumeConfig = runConfig.getVolumeConfiguration();
            if (volumeConfig != null) {
                config.binds(volumeConfig.getBind());
            }

            final NetworkConfig networkConfig = runConfig.getNetworkingConfig();
            if (networkConfig.isCustomNetwork() && networkConfig.hasAliases()) {
                final ContainerNetworkingConfig networkingConfig =
                        new ContainerNetworkingConfig().aliases(networkConfig);
                config.networkingConfig(networkingConfig);
            }

            return config;
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Failed to create contained configuration for [%s]", imageName), e);
        }
    }

    private Map<String, String> mergeLabels(final Map<String, String> labels, final PomLabel runIdLabel) {
        final Map<String, String> ret = new HashMap<>();
        if (labels != null) {
            ret.putAll(labels);
        }
        if (runIdLabel != null) {
            ret.put(runIdLabel.getKey(), runIdLabel.getValue());
        }
        return ret;
    }

    ContainerHostConfig createContainerHostConfig(final RunImageConfiguration runConfig, final PortMapping mappedPorts)
            throws DockerAccessException {
        final RestartPolicy restartPolicy = runConfig.getRestartPolicy();


        final List<String> links = findContainerIdsForLinks(runConfig.getLinks(),
                runConfig.getNetworkingConfig().isCustomNetwork());

        final ContainerHostConfig config = new ContainerHostConfig()
                .extraHosts(runConfig.getExtraHosts())
                .links(links)
                .portBindings(mappedPorts)
                .privileged(runConfig.getPrivileged())
                .shmSize(runConfig.getShmSize())
                .dns(runConfig.getDns())
                .dnsSearch(runConfig.getDnsSearch())
                .capAdd(runConfig.getCapAdd())
                .capDrop(runConfig.getCapDrop())
                .securityOpts(runConfig.getSecurityOpts())
                .memory(runConfig.getMemory())
                .memorySwap(runConfig.getMemorySwap())
                .restartPolicy(restartPolicy.getName(), restartPolicy.getRetry())
                .logConfig(runConfig.getLogConfiguration())
                .tmpfs(runConfig.getTmpfs())
                .ulimits(runConfig.getUlimits());

        addVolumeConfig(config, runConfig);
        addNetworkingConfig(config, runConfig);

        return config;
    }

    private void addNetworkingConfig(final ContainerHostConfig config, final RunImageConfiguration runConfig) throws DockerAccessException {
        final NetworkConfig networkConfig = runConfig.getNetworkingConfig();
        if (networkConfig.isStandardNetwork()) {
            final String alias = networkConfig.getContainerAlias();
            final String containerId = alias != null ? findContainerId(alias, false) : null;
            config.networkMode(networkConfig.getStandardMode(containerId));
        } else if (networkConfig.isCustomNetwork()) {
            config.networkMode(networkConfig.getCustomNetwork());
        }
    }

    private void addVolumeConfig(final ContainerHostConfig config, final RunImageConfiguration runConfig) throws DockerAccessException {
        final VolumeConfiguration volConfig = runConfig.getVolumeConfiguration();
        if (volConfig != null) {
            config.binds(volConfig.getBind())
                    .volumesFrom(findVolumesFromContainers(volConfig.getFrom()));
        }
    }

    private List<String> findContainerIdsForLinks(final List<String> links, final boolean leaveUnresolvedIfNotFound) throws DockerAccessException {
        final List<String> ret = new ArrayList<>();
        for (final String[] link : EnvUtil.splitOnLastColon(links)) {
            final String id = findContainerId(link[0], false);
            if (id != null) {
                ret.add(queryService.getContainerName(id) + ":" + link[1]);
            } else if (leaveUnresolvedIfNotFound) {
                ret.add(link[0] + ":" + link[1]);
            } else {
                throw new DockerAccessException("No container found for image/alias '%s', unable to link", link[0]);
            }
        }
        return ret.size() != 0 ? ret : null;
    }

    // visible for testing
    private List<String> findVolumesFromContainers(final List<String> images) throws DockerAccessException {
        final List<String> list = new ArrayList<>();
        if (images != null) {
            for (final String image : images) {
                final String id = findContainerId(image, true);
                if (id == null) {
                    throw new DockerAccessException("No container found for image/alias '%s', unable to mount volumes", image);
                }

                list.add(queryService.getContainerName(id));
            }
        }
        return list;
    }


    private String calculateContainerName(final String alias, final RunImageConfiguration.NamingStrategy namingStrategy) {
        if (namingStrategy == RunImageConfiguration.NamingStrategy.none) {
            return null;
        }
        if (alias == null) {
            throw new IllegalArgumentException("A naming scheme 'alias' requires an image alias to be set");
        }
        return alias;
    }

    // checkAllContainers: false = only running containers are considered
    private String findContainerId(final String imageNameOrAlias, final boolean checkAllContainers) throws DockerAccessException {
        String id = lookupContainer(imageNameOrAlias);

        // check for external container. The image name is interpreted as a *container name* for that case ...
        if (id == null) {
            final Container container = queryService.getContainer(imageNameOrAlias);
            if (container != null && (checkAllContainers || container.isRunning())) {
                id = container.getId();
            }
        }
        return id;
    }

    private void startContainer(final ImageConfiguration imageConfig, final String id, final PomLabel pomLabel) throws DockerAccessException {
        log.info("%s: Start container %s", imageConfig.getDescription(), id);
        docker.startContainer(id);
        tracker.registerContainer(id, imageConfig, pomLabel);
    }

    private void updateMappedPortsAndAddresses(final String containerId, final PortMapping mappedPorts) throws DockerAccessException {
        final Container container = queryService.getMandatoryContainer(containerId);
        if (container.isRunning()) {
            mappedPorts.updateProperties(container.getPortBindings());
        } else {
            log.warn("Container %s is not running anymore, can not extract dynamic ports", containerId);
        }
    }

    private void shutdown(final ContainerTracker.ContainerShutdownDescriptor descriptor, final boolean keepContainer, final boolean removeVolumes)
            throws DockerAccessException {

        final String containerId = descriptor.getContainerId();
        if (descriptor.getPreStop() != null) {
            try {
                execInContainer(containerId, descriptor.getPreStop(), descriptor.getImageConfiguration());
            } catch (final DockerAccessException e) {
                log.error("%s", e.getMessage());
            }
        }

        final int killGracePeriod = adjustGracePeriod(descriptor.getKillGracePeriod());
        log.debug("shutdown will wait max of %d seconds before removing container", killGracePeriod);

        final long waited;
        if (killGracePeriod == 0) {
            docker.stopContainer(containerId, 0);
            waited = 0;
        } else {
            waited = shutdownAndWait(containerId, killGracePeriod);
        }
        if (!keepContainer) {
            removeContainer(descriptor, removeVolumes, containerId);
        }

        log.info("%s: Stop%s container %s after %s ms",
                descriptor.getDescription(),
                (keepContainer ? "" : " and removed"),
                containerId.substring(0, 12), waited);
    }

    public void createCustomNetworkIfNotExistant(final String customNetwork) throws DockerAccessException {
        if (!queryService.hasNetwork(customNetwork)) {
            docker.createNetwork(new NetworkCreateConfig(customNetwork));
        } else {
            log.debug("Custom Network " + customNetwork + " found");
        }
    }

    public void removeCustomNetworks(final Collection<Network> networks) throws DockerAccessException {
        for (final Network network : networks) {
            docker.removeNetwork(network.getId());
        }
    }

    private int adjustGracePeriod(final int gracePeriod) {
        final int killGracePeriodInSeconds = (gracePeriod + 500) / 1000;
        if (gracePeriod != 0 && killGracePeriodInSeconds == 0) {
            log.warn("A kill grace period of %d ms leads to no wait at all since its rounded to seconds. " +
                    "Please use at least 500 as value for wait.kill", gracePeriod);
        }

        return killGracePeriodInSeconds;
    }

    private void removeContainer(final ContainerTracker.ContainerShutdownDescriptor descriptor, final boolean removeVolumes, final String containerId)
            throws DockerAccessException {
        final int shutdownGracePeriod = descriptor.getShutdownGracePeriod();
        if (shutdownGracePeriod != 0) {
            log.debug("Shutdown: Wait %d ms before removing container", shutdownGracePeriod);
            WaitUtil.sleep(shutdownGracePeriod);
        }
        // Remove the container
        docker.removeContainer(containerId, removeVolumes);
    }

    private long shutdownAndWait(final String containerId, final int killGracePeriodInSeconds) throws DockerAccessException {
        long waited;
        try {
            waited = WaitUtil.wait(killGracePeriodInSeconds, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    docker.stopContainer(containerId, killGracePeriodInSeconds);
                    return null;
                }
            });
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof DockerAccessException) {
                throw (DockerAccessException) e.getCause();
            } else {
                throw new DockerAccessException(e, "failed to stop container id [%s]", containerId);
            }
        } catch (final WaitTimeoutException e) {
            waited = e.getWaited();
            log.warn("Stop container id [%s] timed out after %s ms", containerId, waited);
        }

        return waited;
    }
}
