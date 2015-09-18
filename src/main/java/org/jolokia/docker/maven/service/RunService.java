package org.jolokia.docker.maven.service;/*
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

import org.jolokia.docker.maven.access.*;
import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.model.Container;
import org.jolokia.docker.maven.util.*;


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
    //private final Map<String,ShutdownAction> shutdownActionMap = new LinkedHashMap<>();
    final private ContainerTracker tracker;

    // DAO for accessing the docker daemon
    private DockerAccess docker;

    private QueryService queryService;

    public RunService(DockerAccess docker, QueryService queryService, ContainerTracker tracker, Logger log) {
        this.docker = docker;
        this.queryService = queryService;
        this.tracker = tracker;
        this.log = log;
    }

    /**
     * Create and start a Exec container with the given image configuration.
     * @param containerId container id to run exec command against
     *
     * @return the exec container id
     *
     * @throws DockerAccessException if access to the docker backend fails
     */
    public String execInContainer(String containerId, String command) throws DockerAccessException {
        Arguments arguments = new Arguments();
        arguments.setExec(Arrays.asList(EnvUtil.splitOnSpaceWithEscape(command)));
        String execContainerId = docker.createExecContainer(arguments, containerId);
        docker.startExecContainer(execContainerId);
        return execContainerId;
    }

    /**
     * Create and start a container with the given image configuration.
     * @param imageConfig image configuration holding the run information and the image name
     * @param mappedPorts container port mapping
     * @param mavenProps properties to fill in with dynamically assigned ports
     *
     * @return the container id
     *
     * @throws DockerAccessException if access to the docker backend fails
     */
    public String createAndStartContainer(ImageConfiguration imageConfig,
                                          PortMapping mappedPorts,
                                          Properties mavenProps) throws DockerAccessException {
        RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
        String imageName = imageConfig.getName();
        String containerName = calculateContainerName(imageConfig.getAlias(), runConfig.getNamingStrategy());
        ContainerCreateConfig config = createContainerConfig(imageName, runConfig, mappedPorts, mavenProps);

        String id = docker.createContainer(config, containerName);
        startContainer(imageConfig, id);

        if (mappedPorts.containsDynamicPorts()) {
            updateMappedPorts(id, mappedPorts);
        }

        return id;
    }

    /**
     * Stop a container immediately by id.
     * @param image image configuration for this container
     * @param containerId the container to stop
     * @param keepContainer whether to keep container or to remove them after stoppings
     * @param removeVolumes whether to remove volumes after stopping
     */
    public void stopContainer(ImageConfiguration image,
                              String containerId, boolean keepContainer,
                              boolean removeVolumes)
            throws DockerAccessException {
        ContainerTracker.ContainerShutdownDescriptor descriptor = new ContainerTracker.ContainerShutdownDescriptor(image,containerId);
        shutdown(docker, descriptor, log, keepContainer, removeVolumes);
    }

    /**
     * Lookup up whether a certain has been already started and registered. If so, stop it
     * @param containerId the container to stop
     * @param keepContainer whether to keep container or to remove them after stoppings
     * @param removeVolumes whether to remove volumes after stopping
     *
     * @throws DockerAccessException
     */
    public void stopContainer(String containerId,
                              boolean keepContainer,
                              boolean removeVolumes)
            throws DockerAccessException {
        synchronized (tracker) {
            ContainerTracker.ContainerShutdownDescriptor descriptor = tracker.getContainerShutdownDescriptor(containerId);
            if (descriptor != null) {
                shutdown(docker, descriptor, log, keepContainer, removeVolumes);
                tracker.removeContainerShutdownDescriptor(containerId);
            }
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
                                      boolean removeVolumes)
            throws DockerAccessException {
        synchronized (tracker) {
            for (ContainerTracker.ContainerShutdownDescriptor descriptor : tracker.getAllContainerShutdownDescriptors()) {
                shutdown(docker, descriptor, log, keepContainer, removeVolumes);
            }
            tracker.resetContainers();
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
    public void addShutdownHookForStoppingContainers(final boolean keepContainer, final boolean removeVolumes) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    stopStartedContainers(keepContainer, removeVolumes);
                } catch (DockerAccessException e) {
                    log.error("Error while stopping containers: " + e);
                }
            }
        });
    }

    // ================================================================================================

    private List<StartOrderResolver.Resolvable> convertToResolvables(List<ImageConfiguration> images) {
        List<StartOrderResolver.Resolvable> ret = new ArrayList<>();
        for (ImageConfiguration config : images) {
            if (config.getRunConfiguration().skip()) {
                log.info(config.getDescription() + ": Skipped running");
            } else {
                ret.add(config);
            }
        }
        return ret;
    }

    // visible for testing
    ContainerCreateConfig createContainerConfig(String imageName, RunImageConfiguration runConfig, PortMapping mappedPorts,
                                                Properties mavenProps)
            throws DockerAccessException {
        try {
            ContainerCreateConfig config = new ContainerCreateConfig(imageName)
                    .hostname(runConfig.getHostname())
                    .domainname(runConfig.getDomainname())
                    .user(runConfig.getUser())
                    .workingDir(runConfig.getWorkingDir())
                    .memory(runConfig.getMemory())
                    .memorySwap(runConfig.getMemorySwap())
                    .entrypoint(runConfig.getEntrypoint())
                    .exposedPorts(mappedPorts.getContainerPorts())
                    .environment(runConfig.getEnvPropertyFile(), runConfig.getEnv(), mavenProps)
                    .labels(runConfig.getLabels())
                    .command(runConfig.getCmd())
                    .hostConfig(createContainerHostConfig(runConfig, mappedPorts));
            VolumeConfiguration volumeConfig = runConfig.getVolumeConfiguration();
            if (volumeConfig != null) {
                config.binds(volumeConfig.getBind());
            }
            return config;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Failed to create contained configuration for [%s]", imageName), e);
        }
    }

    ContainerHostConfig createContainerHostConfig(RunImageConfiguration runConfig, PortMapping mappedPorts)
            throws DockerAccessException {
        RestartPolicy restartPolicy = runConfig.getRestartPolicy();

        List<String> links = findLinksContainers(runConfig.getLinks());

        ContainerHostConfig config = new ContainerHostConfig()
                .extraHosts(runConfig.getExtraHosts())
                .links(links)
                .portBindings(mappedPorts)
                .privileged(runConfig.getPrivileged())
                .dns(runConfig.getDns())
                .dnsSearch(runConfig.getDnsSearch())
                .capAdd(runConfig.getCapAdd())
                .capDrop(runConfig.getCapDrop())
                .restartPolicy(restartPolicy.getName(), restartPolicy.getRetry());
        VolumeConfiguration volConfig = runConfig.getVolumeConfiguration();
        if (volConfig != null) {
            config.binds(volConfig.getBind())
                  .volumesFrom(findVolumesFromContainers(volConfig.getFrom()));
        }
        return config;
    }

    List<String> findLinksContainers(List<String> links) throws DockerAccessException {
        List<String> ret = new ArrayList<>();
        for (String[] link : EnvUtil.splitOnLastColon(links)) {
            String id = findContainerId(link[0], false);
            if (id == null) {
                throw new DockerAccessException("No container found for image/alias '%s', unable to link", link[0]);
            }
            ret.add(queryService.getContainerName(id) + ":" + link[1]);
        }
        return ret.size() != 0 ? ret : null;
    }

    // visible for testing
    List<String> findVolumesFromContainers(List<String> images) throws DockerAccessException {
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
            Container container = queryService.getContainerByName(imageNameOrAlias);
            if (container != null && (checkAllContainers || container.isRunning())) {
                id = container.getId();
            }
        }
        return id;
    }

    private void startContainer(ImageConfiguration imageConfig, String id) throws DockerAccessException {
        log.info(imageConfig.getDescription() + ": Start container " + id);
        docker.startContainer(id);
        tracker.registerContainer(id, imageConfig);
    }

    private void updateMappedPorts(String containerId, PortMapping mappedPorts) throws DockerAccessException {
        Container container = queryService.getContainer(containerId);
        mappedPorts.updateVariablesWithDynamicPorts(container.getPortBindings());
    }

    private void shutdown(DockerAccess access, ContainerTracker.ContainerShutdownDescriptor descriptor,
                          Logger log, boolean keepContainer, boolean removeVolumes)
            throws DockerAccessException {

        String containerId = descriptor.getContainerId();
        if (descriptor.getPreStop() != null) {
            try {
                execInContainer(containerId, descriptor.getPreStop());
            } catch (DockerAccessException e) {
                log.error(e.getMessage());
            }
        }
        // Stop the container
        int killAfterStopPeriod = descriptor.getKillAfterStopPeriod();
        access.stopContainer(containerId, killAfterStopPeriod);
        if (killAfterStopPeriod > 0) {
            log.debug("Shutdown: Wait " + killAfterStopPeriod + " s after stopping and before killing container");
            WaitUtil.sleep(killAfterStopPeriod * 1000);
        }
        if (!keepContainer) {
            int shutdownGracePeriod = descriptor.getShutdownGracePeriod();
            if (shutdownGracePeriod != 0) {
                log.debug("Shutdown: Wait " + shutdownGracePeriod + " ms before removing container");
                WaitUtil.sleep(shutdownGracePeriod);
            }
            // Remove the container
            access.removeContainer(containerId, removeVolumes);
        }
        log.info(descriptor.getDescription() + ": Stop" + (keepContainer ? "" : " and remove") + " container " +
                 containerId.substring(0, 12));
    }
}
