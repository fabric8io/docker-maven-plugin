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

import java.util.*;

import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.log.LogOutputSpecFactory;
import io.fabric8.maven.docker.access.*;
import io.fabric8.maven.docker.config.*;
import io.fabric8.maven.docker.model.Network;
import io.fabric8.maven.docker.util.*;


/**
 * Service class for helping in running containers.
 *
 * @author roland
 * @since 16/06/15
 */
public class RunService {

    // logger delegated from top
    private Logger log;

    // Action to be used when doing a shutdown
    final private ContainerTracker tracker;

    // DAO for accessing the docker daemon
    private DockerAccess docker;

    private QueryService queryService;

    private final LogOutputSpecFactory logConfig;

    public RunService(DockerAccess docker,
                      QueryService queryService,
                      ContainerTracker tracker,
                      LogOutputSpecFactory logConfig,
                      Logger log) {
        this.docker = docker;
        this.queryService = queryService;
        this.tracker = tracker;
        this.log = log;
        this.logConfig = logConfig;
    }

    /**
     * Create and start a Exec container with the given image configuration.
     * @param containerId container id to run exec command against
     * @param command command to execute
     * @param imageConfiguration configuration of the container's image
     * @return the exec container id
     *
     * @throws DockerAccessException if access to the docker backend fails
     */
    public String execInContainer(String containerId, String command, ImageConfiguration imageConfiguration)
            throws DockerAccessException {
        Arguments arguments = new Arguments();
        arguments.setExec(Arrays.asList(EnvUtil.splitOnSpaceWithEscape(command)));
        String execContainerId = docker.createExecContainer(containerId, arguments);
        docker.startExecContainer(execContainerId, logConfig.createSpec(containerId, imageConfiguration));
        return execContainerId;
    }

    /**
     * Create and start a container with the given image configuration.
     * @param imageConfig image configuration holding the run information and the image name
     * @param mappedPorts container port mapping
     * @param mavenProps properties to fill in with dynamically assigned ports
     * @param pomLabel label to tag the started container with
     *
     * @return the container id
     *
     * @throws DockerAccessException if access to the docker backend fails
     */
    public String createAndStartContainer(ImageConfiguration imageConfig,
                                          PortMapping mappedPorts,
                                          PomLabel pomLabel,
                                          Properties mavenProps) throws DockerAccessException {
        RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
        String imageName = imageConfig.getName();
        String containerName = calculateContainerName(imageConfig.getAlias(), runConfig.getNamingStrategy());
        ContainerCreateConfig config = createContainerConfig(imageName, runConfig, mappedPorts, pomLabel, mavenProps);

        String id = docker.createContainer(config, containerName);
        startContainer(imageConfig, id, pomLabel);

        if (mappedPorts.needsPropertiesUpdate()) {
            updateMappedPortsAndAddresses(id, mappedPorts);
        }

        return id;
    }

    /**
     * Stop a container immediately by id.
     * @param containerId the container to stop
     * @param imageConfig image configuration for this container
     * @param keepContainer whether to keep container or to remove them after stoppings
     * @param removeVolumes whether to remove volumes after stopping
     */
    public void stopContainer(String containerId,
                              ImageConfiguration imageConfig,
                              boolean keepContainer,
                              boolean removeVolumes)
            throws DockerAccessException {
        ContainerTracker.ContainerShutdownDescriptor descriptor =
                new ContainerTracker.ContainerShutdownDescriptor(imageConfig,containerId);
        shutdown(docker, descriptor, log, keepContainer, removeVolumes);
    }

    /**
     * Lookup up whether a certain has been already started and registered. If so, stop it
     *
     * @param containerId the container to stop
     * @param keepContainer whether to keep container or to remove them after stoppings
     * @param removeVolumes whether to remove volumes after stopping
     *
     * @throws DockerAccessException
     */
    public void stopPreviouslyStartedContainer(String containerId,
                                               boolean keepContainer,
                                               boolean removeVolumes)
            throws DockerAccessException {
        ContainerTracker.ContainerShutdownDescriptor descriptor = tracker.removeContainer(containerId);
        if (descriptor != null) {
            shutdown(docker, descriptor, log, keepContainer, removeVolumes);
        }
    }

    /**
     * Stop all registered container
     * @param keepContainer whether to keep container or to remove them after stoppings
     * @param removeVolumes whether to remove volumes after stopping
     *
     * @throws DockerAccessException if during stopping of a container sth fails
     */
    public void stopStartedContainers(boolean keepContainer,
                                      boolean removeVolumes,
                                      boolean removeCustomNetworks,
                                      PomLabel pomLabel)
            throws DockerAccessException {
        Set<Network> networksToRemove = new HashSet<>();
        for (ContainerTracker.ContainerShutdownDescriptor descriptor : tracker.removeShutdownDescriptors(pomLabel)) {
            collectCustomNetworks(networksToRemove, descriptor, removeCustomNetworks);
            shutdown(docker, descriptor, log, keepContainer, removeVolumes);
        }
        removeCustomNetworks(networksToRemove);
    }

    private void collectCustomNetworks(Set<Network> networksToRemove, ContainerTracker.ContainerShutdownDescriptor descriptor, boolean removeCustomNetworks) throws DockerAccessException {
        final NetworkingMode networkingMode = descriptor.getImageConfiguration().getRunConfiguration().getNetworkingMode();
        if (removeCustomNetworks && networkingMode.isCustomNetwork()) {
           networksToRemove.add(queryService.getNetworkByName(networkingMode.getCustomNetwork()));
        }
    }

    /**
     * Lookup a container that has been started
     *
     * @param lookup a container by id or alias
     * @return the container id if the container exists, <code>null</code> otherwise.
     */
    public String lookupContainer(String lookup) {
        return tracker.lookupContainer(lookup);
    }

    /**
     * Get the proper order for images to start
     * @param images list of images for which the order should be created
     * @return list of images in the right startup order
     */
    public List<StartOrderResolver.Resolvable> getImagesConfigsInOrder(QueryService queryService, List<ImageConfiguration> images) {
        return StartOrderResolver.resolve(queryService, convertToResolvables(images));
    }

    /**
     * Create port mapping for a specific configuration as it can be used when creating containers
     *
     * @param runConfig the cun configuration
     * @param properties properties to lookup variables
     * @return the portmapping
     */
    public PortMapping getPortMapping(RunImageConfiguration runConfig, Properties properties) {
        try {
            return new PortMapping(runConfig.getPorts(), properties);
        } catch (IllegalArgumentException exp) {
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
                } catch (DockerAccessException e) {
                    log.error("Error while stopping containers: %s", e.getMessage());
                }
            }
        });
    }

    private List<StartOrderResolver.Resolvable> convertToResolvables(List<ImageConfiguration> images) {
        List<StartOrderResolver.Resolvable> ret = new ArrayList<>();
        for (ImageConfiguration config : images) {
            if (config.getRunConfiguration().skip()) {
                log.info("%s: Skipped running", config.getDescription());
            } else {
                ret.add(config);
            }
        }
        return ret;
    }

    // visible for testing
    ContainerCreateConfig createContainerConfig(String imageName, RunImageConfiguration runConfig, PortMapping mappedPorts,
                                                PomLabel pomLabel, Properties mavenProps)
            throws DockerAccessException {
        try {
            ContainerCreateConfig config = new ContainerCreateConfig(imageName)
                    .hostname(runConfig.getHostname())
                    .domainname(runConfig.getDomainname())
                    .user(runConfig.getUser())
                    .workingDir(runConfig.getWorkingDir())
                    .entrypoint(runConfig.getEntrypoint())
                    .exposedPorts(mappedPorts.getContainerPorts())
                    .environment(runConfig.getEnvPropertyFile(), runConfig.getEnv(), mavenProps)
                    .labels(mergeLabels(runConfig.getLabels(), pomLabel))
                    .command(runConfig.getCmd())
                    .hostConfig(createContainerHostConfig(runConfig, mappedPorts));
            VolumeConfiguration volumeConfig = runConfig.getVolumeConfiguration();
            if (volumeConfig != null) {
                config.binds(volumeConfig.getBind());
            }

            if(runConfig.getNetworkingMode().isCustomNetwork() && runConfig.getNetworkAlias() != null && !runConfig.getNetworkAlias().isEmpty()) {
                config.networkingConfig(new ContainerNetworkingConfig().endpointsConfig(
                        Collections.singletonMap(
                                runConfig.getNetworkingMode().getCustomNetwork(),
                                new ContainerNetworkingEndpointsConfig()
                                        .aliases(runConfig.getNetworkAlias()))));
            }

            return config;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Failed to create contained configuration for [%s]", imageName), e);
        }
    }

    private Map<String, String> mergeLabels(Map<String, String> labels, PomLabel runIdLabel) {
        Map<String,String> ret = new HashMap<>();
        if (labels != null) {
            ret.putAll(labels);
        }
        if (runIdLabel != null) {
            ret.put(runIdLabel.getKey(), runIdLabel.getValue());
        }
        return ret;
    }

    ContainerHostConfig createContainerHostConfig(RunImageConfiguration runConfig, PortMapping mappedPorts)
            throws DockerAccessException {
        RestartPolicy restartPolicy = runConfig.getRestartPolicy();


        List<String> links = findContainerIdsForLinks(runConfig.getLinks(),runConfig.getNetworkingMode().isCustomNetwork());

        ContainerHostConfig config = new ContainerHostConfig()
                .extraHosts(runConfig.getExtraHosts())
                .links(links)
                .portBindings(mappedPorts)
                .privileged(runConfig.getPrivileged())
                .shmSize(runConfig.getShmSize())
                .dns(runConfig.getDns())
                .dnsSearch(runConfig.getDnsSearch())
                .capAdd(runConfig.getCapAdd())
                .capDrop(runConfig.getCapDrop())
                .memory(runConfig.getMemory())
                .memorySwap(runConfig.getMemorySwap())
                .restartPolicy(restartPolicy.getName(), restartPolicy.getRetry())
                .logConfig(runConfig.getLogConfiguration())
                .ulimits(runConfig.getUlimits());

        addVolumeConfig(config, runConfig);
        addNetworkingConfig(config, runConfig);

        return config;
    }

    private void addNetworkingConfig(ContainerHostConfig config, RunImageConfiguration runConfig) throws DockerAccessException {
        NetworkingMode netMode = runConfig.getNetworkingMode();
        if (netMode.isStandardMode()) {
            String alias = netMode.getContainerAlias();
            String containerId = alias != null ? findContainerId(alias, false) : null;
            config.networkConfig(netMode.getStandardMode(containerId));
        } else if (netMode.isCustomNetwork()) {
            config.networkConfig(netMode.getCustomNetwork());
        }
    }

    private void addVolumeConfig(ContainerHostConfig config, RunImageConfiguration runConfig) throws DockerAccessException {
        VolumeConfiguration volConfig = runConfig.getVolumeConfiguration();
        if (volConfig != null) {
            config.binds(volConfig.getBind())
                  .volumesFrom(findVolumesFromContainers(volConfig.getFrom()));
        }
    }

    private List<String> findContainerIdsForLinks(List<String> links, boolean leaveUnresolvedIfNotFound) throws DockerAccessException {
        List<String> ret = new ArrayList<>();
        for (String[] link : EnvUtil.splitOnLastColon(links)) {
            String id = findContainerId(link[0], false);
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
    private List<String> findVolumesFromContainers(List<String> images) throws DockerAccessException {
        List<String> list = new ArrayList<>();
        if (images != null) {
            for (String image : images) {
                String id = findContainerId(image, true);
                if (id == null) {
                    throw new DockerAccessException("No container found for image/alias '%s', unable to mount volumes", image);
                }

                list.add(queryService.getContainerName(id));
            }
        }
        return list;
    }


    private String calculateContainerName(String alias, RunImageConfiguration.NamingStrategy namingStrategy) {
        if (namingStrategy == RunImageConfiguration.NamingStrategy.none) {
            return null;
        }
        if (alias == null) {
            throw new IllegalArgumentException("A naming scheme 'alias' requires an image alias to be set");
        }
        return alias;
    }

    // checkAllContainers: false = only running containers are considered
    private String findContainerId(String imageNameOrAlias, boolean checkAllContainers) throws DockerAccessException {
        String id = lookupContainer(imageNameOrAlias);

        // check for external container. The image name is interpreted as a *container name* for that case ...
        if (id == null) {
            Container container = queryService.getContainer(imageNameOrAlias);
            if (container != null && (checkAllContainers || container.isRunning())) {
                id = container.getId();
            }
        }
        return id;
    }

    private void startContainer(ImageConfiguration imageConfig, String id, PomLabel pomLabel) throws DockerAccessException {
        log.info("%s: Start container %s",imageConfig.getDescription(), id);
        docker.startContainer(id);
        tracker.registerContainer(id, imageConfig, pomLabel);
    }

    private void updateMappedPortsAndAddresses(String containerId, PortMapping mappedPorts) throws DockerAccessException {
        Container container = queryService.getMandatoryContainer(containerId);
        if (container.isRunning()) {
            mappedPorts.updateProperties(container.getPortBindings());
        } else {
            log.warn("Container %s is not running anymore, can not extract dynamic ports",containerId);
        }
    }

    private void shutdown(DockerAccess access,
                          ContainerTracker.ContainerShutdownDescriptor descriptor,
                          Logger log, boolean keepContainer, boolean removeVolumes)
            throws DockerAccessException {

        String containerId = descriptor.getContainerId();
        if (descriptor.getPreStop() != null) {
            try {
                execInContainer(containerId, descriptor.getPreStop(), descriptor.getImageConfiguration());
            } catch (DockerAccessException e) {
                log.error("%s", e.getMessage());
            }
        }
        // Stop the container
        int killGracePeriod = descriptor.getKillGracePeriod();
        int killGracePeriodInSeconds = (killGracePeriod + 500) / 1000;
        if (killGracePeriod != 0 && killGracePeriodInSeconds == 0) {
            log.warn("A kill grace period of %d ms leads to no wait at all since its rounded to seconds. " +
                     "Please use at least 500 as value for wait.kill",killGracePeriod);
        }
        access.stopContainer(containerId, killGracePeriodInSeconds);
        if (killGracePeriod > 0) {
            log.debug("Shutdown: Wait %d s after stopping and before killing container", killGracePeriodInSeconds);
            WaitUtil.sleep(killGracePeriodInSeconds * 1000);
        }
        if (!keepContainer) {
            int shutdownGracePeriod = descriptor.getShutdownGracePeriod();
            if (shutdownGracePeriod != 0) {
                log.debug("Shutdown: Wait %d ms before removing container", shutdownGracePeriod);
                WaitUtil.sleep(shutdownGracePeriod);
            }
            // Remove the container
            access.removeContainer(containerId, removeVolumes);
        }

        log.info("%s: Stop%s container %s",
                 descriptor.getDescription(),
                 (keepContainer ? "" : " and remove"),
                 containerId.substring(0, 12));
    }

    public void createCustomNetworkIfNotExistant(String customNetwork) throws DockerAccessException {
        if (!queryService.hasNetwork(customNetwork)) {
            docker.createNetwork(new NetworkCreateConfig(customNetwork));
        } else {
            log.debug("Custom Network " + customNetwork + " found");
        }
    }

    public void removeCustomNetworks(Collection<Network> networks) throws DockerAccessException {
        for (Network network : networks) {
            docker.removeNetwork(network.getId());
        }
    }
}
