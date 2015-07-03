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
import org.jolokia.docker.maven.util.*;

/**
 * Service class for helping in running containers.
 *
 * @author roland
 * @since 16/06/15
 */
public class RunService {

    // Map holding associations between started containers and their images via name and aliases
    // Key: Image, Value: Container
    private Map<String, String> containerImageNameMap = new HashMap<>();

    // Key: Alias, Value: Image
    private Map<String, String> imageAliasMap = new HashMap<>();

    // logger delegated from top
    private Logger log;

    // Action to be used when doing a shutdown
    private final Map<String,ShutdownAction> shutdownActionMap = new LinkedHashMap<>();

    /**
     * Create and start a container with the given image configuration.
     *
     * @param docker the docker access object
     * @param imageConfig image configuration holding the run information and the image name
     * @param mappedPorts container port mapping
     * @param mavenProps properties to fill in with dynamically assigned ports
     * @return the container id
     *
     * @throws DockerAccessException if access to the docker backend fails
     */
    public String createAndStartContainer(DockerAccess docker,
                                          ImageConfiguration imageConfig,
                                          PortMapping mappedPorts,
                                          Properties mavenProps) throws DockerAccessException {
        RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
        String imageName = imageConfig.getName();
        String containerName = calculateContainerName(imageConfig.getAlias(), runConfig.getNamingStrategy());
        ContainerCreateConfig config = createContainerConfig(docker, imageName, runConfig, mappedPorts, mavenProps);

        String id = docker.createContainer(config, containerName);
        startContainer(docker, imageConfig, id);
        return id;
    }

    /**
     * Stop a container immediately by id.
     *
     * @param access access object for contacting the docker daemon
     * @param image image configuration for this container
     * @param containerId the container to stop
     * @param keepContainer whether to keep container or to remove them after stoppings
     * @param removeVolumes whether to remove volumes after stopping
     */
    public void stopContainer(DockerAccess access,
                              ImageConfiguration image, String containerId,
                              boolean keepContainer, boolean removeVolumes)
            throws DockerAccessException {
        new ShutdownAction(image,containerId).shutdown(access, log, keepContainer, removeVolumes);
    }

    /**
     * Lookup up whether a certain has been already started and registered. If so, stop it
     *
     * @param docker docker access object
     * @param containerId the container to stop
     * @param keepContainer whether to keep container or to remove them after stoppings
     * @param removeVolumes whether to remove volumes after stopping
     * @throws DockerAccessException
     */
    public void stopContainer(DockerAccess docker,
                              String containerId,
                              boolean keepContainer, boolean removeVolumes)
            throws DockerAccessException {
        synchronized (shutdownActionMap) {
            ShutdownAction shutdownAction = shutdownActionMap.get(containerId);
            if (shutdownAction != null) {
                shutdownAction.shutdown(docker, log, keepContainer, removeVolumes);
                shutdownActionMap.remove(containerId);
            }
        }
    }

    /**
     * Stop all registered container
     *
     * @param docker docker access object
     * @param keepContainer whether to keep container or to remove them after stoppings
     * @param removeVolumes whether to remove volumes after stopping
     * @throws DockerAccessException if during stopping of a container sth fails
     */
    public void stopStartedContainers(DockerAccess docker,
                                      boolean keepContainer, boolean removeVolumes)
            throws DockerAccessException {
        synchronized (shutdownActionMap) {
            List<ShutdownAction> actions = new ArrayList<>(shutdownActionMap.values());
            Collections.reverse(actions);
            for (ShutdownAction action : actions) {
                action.shutdown(docker, log, keepContainer, removeVolumes);
            }
            shutdownActionMap.clear();
        }
    }

    /**
     * Lookup a container that has been started
     *
     * @param lookup a container by id or alias
     * @return the container id if the container exists, <code>null</code> otherwise.
     */
    public String lookupContainer(String lookup) {
        String image = imageAliasMap.containsKey(lookup) ? imageAliasMap.get(lookup) : lookup;
        return containerImageNameMap.get(image);
    }

    /**
     * Get the proper order for images to start
     * @param images list of images for which the order should be created
     * @return list of images in the right startup order
     */
    public List<StartOrderResolver.Resolvable> getImagesConfigsInOrder(List<ImageConfiguration> images) {
        return StartOrderResolver.resolve(convertToResolvables(images));
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
     * Init method for late initialization
     *
     * @param log log to used diagnostic messages
     */
    public void initLog(Logger log) {
        this.log = log;
    }

    /**
     * Add a shutdown hook in order to stop all registered containers
     *
     * @param docker docker access object
     */
    public void addShutdownHookForStoppingContainers(final DockerAccess docker, final boolean keepContainer, final boolean removeVolumes) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    stopStartedContainers(docker, keepContainer, removeVolumes);
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
            ret.add(config);
        }
        return ret;
    }

    // visible for testing
    ContainerCreateConfig createContainerConfig(DockerAccess docker, String imageName, RunImageConfiguration runConfig,
                                                PortMapping mappedPorts, Properties mavenProps)
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
                    .hostConfig(createContainerHostConfig(docker, runConfig, mappedPorts));
            VolumeConfiguration volumeConfig = runConfig.getVolumeConfiguration();
            if (volumeConfig != null) {
                config.binds(volumeConfig.getBind());
            }
            return config;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Failed to create contained configuration for [%s]", imageName), e);
        }
    }


    ContainerHostConfig createContainerHostConfig(DockerAccess docker, RunImageConfiguration runConfig, PortMapping mappedPorts)
            throws DockerAccessException {
        RestartPolicy restartPolicy = runConfig.getRestartPolicy();

        List<String> links = findLinksWithContainerNames(docker, runConfig.getLinks());

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
                  .volumesFrom(findContainersForImages(volConfig.getFrom()));
        }
        return config;
    }


    List<String> findLinksWithContainerNames(DockerAccess docker, List<String> links) throws DockerAccessException {
        List<String> ret = new ArrayList<>();
        for (String[] link : EnvUtil.splitOnLastColon(links)) {
            String container = lookupContainer(link[0]);
            if (container == null) {
                throw new DockerAccessException("Cannot find container for " + link[0] + " while preparing links");
            }
            ret.add(docker.getContainerName(container) + ":" + link[1]);
        }
        return ret.size() != 0 ? ret : null;
    }

    // visible for testing
    List<String> findContainersForImages(List<String> images) {
        if (images != null) {
            List<String> containers = new ArrayList<>();
            for (String image : images) {
                String container = lookupContainer(image);
                if (container == null) {
                    throw new IllegalStateException("No container for image " + image + " started.");
                }
                containers.add(container);
            }
            return containers;
        }
        return null;
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

    private void startContainer(DockerAccess docker, ImageConfiguration imageConfig, String id) throws DockerAccessException {
        log.info(imageConfig.getDescription() + ": Start container " + id.substring(0, 12));
        docker.startContainer(id);
        shutdownActionMap.put(id, new ShutdownAction(imageConfig, id));
        updateImageToContainerMapping(imageConfig, id);
    }

    private void updateImageToContainerMapping(ImageConfiguration imageConfig, String id) {
        // Register name -> containerId and alias -> name
        containerImageNameMap.put(imageConfig.getName(), id);
        if (imageConfig.getAlias() != null) {
            imageAliasMap.put(imageConfig.getAlias(), imageConfig.getName());
        }
    }

}
