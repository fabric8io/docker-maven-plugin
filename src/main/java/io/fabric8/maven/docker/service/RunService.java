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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import io.fabric8.maven.docker.access.ContainerCreateConfig;
import io.fabric8.maven.docker.access.ContainerHostConfig;
import io.fabric8.maven.docker.access.ContainerNetworkingConfig;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.ExecException;
import io.fabric8.maven.docker.access.NetworkCreateConfig;
import io.fabric8.maven.docker.access.PortMapping;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.NetworkConfig;
import io.fabric8.maven.docker.config.RestartPolicy;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.config.RunVolumeConfiguration;
import io.fabric8.maven.docker.config.VolumeConfiguration;
import io.fabric8.maven.docker.log.LogOutputSpecFactory;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.model.ContainerDetails;
import io.fabric8.maven.docker.model.ExecDetails;
import io.fabric8.maven.docker.model.Network;
import io.fabric8.maven.docker.util.ContainerNamingUtil;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.GavLabel;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.StartOrderResolver;
import io.fabric8.maven.docker.wait.WaitTimeoutException;
import io.fabric8.maven.docker.wait.WaitUtil;

import static io.fabric8.maven.docker.util.VolumeBindingUtil.resolveRelativeVolumeBindings;


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
        throws DockerAccessException, ExecException {
        Arguments arguments = new Arguments();
        arguments.setExec(Arrays.asList(EnvUtil.splitOnSpaceWithEscape(command)));
        String execContainerId = docker.createExecContainer(containerId, arguments);
        docker.startExecContainer(execContainerId, logConfig.createSpec(containerId, imageConfiguration));

        ExecDetails execContainer = docker.getExecContainer(execContainerId);
        Integer exitCode = execContainer.getExitCode();
        if (exitCode != null && exitCode != 0) {
            ContainerDetails container = docker.getContainer(containerId);
            throw new ExecException(execContainer, container);
        }
        return execContainerId;
    }

    /**
     * Create and start a container with the given image configuration.
     * @param imageConfig image configuration holding the run information and the image name
     * @param portMapping container port mapping
     * @param gavLabel label to tag the started container with
     *
     * @param properties properties to fill in with dynamically assigned ports
     * @param defaultContainerNamePattern pattern to use for naming containers. Can be null in which case a default pattern is used
     * @param buildTimestamp date which should be used as the timestamp when calculating container names
     * @return the container id
     *
     * @throws DockerAccessException if access to the docker backend fails
     */
    public String createAndStartContainer(ImageConfiguration imageConfig,
                                          PortMapping portMapping,
                                          GavLabel gavLabel,
                                          Properties properties,
                                          File baseDir,
                                          String defaultContainerNamePattern,
                                          Date buildTimestamp) throws DockerAccessException {
        RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
        String imageName = imageConfig.getName();

        Collection<Container> existingContainers = queryService.getContainersForImage(imageName, true);
        String containerName = ContainerNamingUtil.formatContainerName(imageConfig, defaultContainerNamePattern, buildTimestamp, existingContainers);

        ContainerCreateConfig config = createContainerConfig(imageName, runConfig, portMapping, gavLabel, properties, baseDir);

        String id = docker.createContainer(config, containerName);
        startContainer(imageConfig, id, gavLabel);

        if (portMapping.needsPropertiesUpdate()) {
            updateMappedPortsAndAddresses(id, portMapping);
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
        throws DockerAccessException, ExecException {
        ContainerTracker.ContainerShutdownDescriptor descriptor =
                new ContainerTracker.ContainerShutdownDescriptor(imageConfig, containerId);
        shutdown(descriptor, keepContainer, removeVolumes);
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
        throws DockerAccessException, ExecException {
        ContainerTracker.ContainerShutdownDescriptor descriptor = tracker.removeContainer(containerId);
        if (descriptor != null) {
            shutdown(descriptor, keepContainer, removeVolumes);
        }
    }

    /**
     * Stop all registered container
     * @param keepContainer whether to keep container or to remove them after stopping
     * @param removeVolumes whether to remove volumes after stopping
     * @throws DockerAccessException if during stopping of a container sth fails
     */
    public void stopStartedContainers(boolean keepContainer,
                                      boolean removeVolumes,
                                      boolean removeCustomNetworks,
                                      GavLabel gavLabel)
        throws DockerAccessException, ExecException {
        Set<Network> networksToRemove = new HashSet<>();
        for (ContainerTracker.ContainerShutdownDescriptor descriptor : tracker.removeShutdownDescriptors(gavLabel)) {
            collectCustomNetworks(networksToRemove, descriptor, removeCustomNetworks);
            shutdown(descriptor, keepContainer, removeVolumes);
        }
        removeCustomNetworks(networksToRemove);
    }

    private void collectCustomNetworks(Set<Network> networksToRemove, ContainerTracker.ContainerShutdownDescriptor descriptor, boolean removeCustomNetworks) throws DockerAccessException {
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
    public PortMapping createPortMapping(RunImageConfiguration runConfig, Properties properties) {
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
                } catch (DockerAccessException | ExecException e) {
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
                                                GavLabel gavLabel, Properties mavenProps, File baseDir)
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
                    .labels(mergeLabels(runConfig.getLabels(), gavLabel))
                    .command(runConfig.getCmd())
                    .hostConfig(createContainerHostConfig(runConfig, mappedPorts, baseDir));
            RunVolumeConfiguration volumeConfig = runConfig.getVolumeConfiguration();
            if (volumeConfig != null) {
                resolveRelativeVolumeBindings(baseDir, volumeConfig);
                config.binds(volumeConfig.getBind());
            }

            NetworkConfig networkConfig = runConfig.getNetworkingConfig();
            if(networkConfig.isCustomNetwork() && networkConfig.hasAliases()) {
                ContainerNetworkingConfig networkingConfig =
                    new ContainerNetworkingConfig()
                        .aliases(networkConfig);
                config.networkingConfig(networkingConfig);
            }

            return config;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Failed to create contained configuration for [%s]", imageName), e);
        }
    }

    private Map<String, String> mergeLabels(Map<String, String> labels, GavLabel runIdLabel) {
        Map<String,String> ret = new HashMap<>();
        if (labels != null) {
            ret.putAll(labels);
        }
        if (runIdLabel != null) {
            ret.put(runIdLabel.getKey(), runIdLabel.getValue());
        }
        return ret;
    }

    ContainerHostConfig createContainerHostConfig(RunImageConfiguration runConfig, PortMapping mappedPorts, File baseDir)
            throws DockerAccessException {
        RestartPolicy restartPolicy = runConfig.getRestartPolicy();


        List<String> links = findContainerIdsForLinks(runConfig.getLinks(),
                                                      runConfig.getNetworkingConfig().isCustomNetwork());

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
                .securityOpts(runConfig.getSecurityOpts())
                .memory(runConfig.getMemory())
                .memorySwap(runConfig.getMemorySwap())
                .restartPolicy(restartPolicy.getName(), restartPolicy.getRetry())
                .logConfig(runConfig.getLogConfiguration())
                .tmpfs(runConfig.getTmpfs())
                .ulimits(runConfig.getUlimits())
                .cpuShares(runConfig.getCpuShares())
                .cpus(runConfig.getCpus())
                .cpuSet(runConfig.getCpuSet())
                .readonlyRootfs(runConfig.getReadOnly())
                .autoRemove(runConfig.getAutoRemove());

        addVolumeConfig(config, runConfig, baseDir);
        addNetworkingConfig(config, runConfig);

        return config;
    }

    private void addNetworkingConfig(ContainerHostConfig config, RunImageConfiguration runConfig) throws DockerAccessException {
        NetworkConfig networkConfig = runConfig.getNetworkingConfig();
        if (networkConfig.isStandardNetwork()) {
            String alias = networkConfig.getContainerAlias();
            String containerId = alias != null ? findContainerId(alias, false) : null;
            config.networkMode(networkConfig.getStandardMode(containerId));
        } else if (networkConfig.isCustomNetwork()) {
            config.networkMode(networkConfig.getCustomNetwork());
        }
    }

    private void addVolumeConfig(ContainerHostConfig config, RunImageConfiguration runConfig, File baseDir) throws DockerAccessException {
        RunVolumeConfiguration volConfig = runConfig.getVolumeConfiguration();
        if (volConfig != null) {
            resolveRelativeVolumeBindings(baseDir, volConfig);
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

    private void startContainer(ImageConfiguration imageConfig, String id, GavLabel gavLabel) throws DockerAccessException {
        log.info("%s: Start container %s",imageConfig.getDescription(), id);
        docker.startContainer(id);
        tracker.registerContainer(id, imageConfig, gavLabel);
    }

    private void updateMappedPortsAndAddresses(String containerId, PortMapping mappedPorts) throws DockerAccessException {
        Container container = queryService.getMandatoryContainer(containerId);
        if (container.isRunning()) {
            mappedPorts.updateProperties(container.getPortBindings());
        } else {
            log.warn("Container %s is not running anymore, can not extract dynamic ports",containerId);
        }
    }

    private void shutdown(ContainerTracker.ContainerShutdownDescriptor descriptor, boolean keepContainer, boolean removeVolumes)
        throws DockerAccessException, ExecException {

        String containerId = descriptor.getContainerId();
        if (descriptor.getPreStop() != null) {
            try {
                execInContainer(containerId, descriptor.getPreStop(), descriptor.getImageConfiguration());
            } catch (DockerAccessException e) {
                log.error("%s", e.getMessage());
            } catch (ExecException e) {
                if (descriptor.isBreakOnError()) {
                    throw e;
                } else {
                    log.warn("Cannot run preStop: %s", e.getMessage());
                }
            }
        }

        int killGracePeriod = adjustGracePeriod(descriptor.getKillGracePeriod());
        log.debug("shutdown will wait max of %d seconds before removing container", killGracePeriod);

        long waited;
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

    private int adjustGracePeriod(int gracePeriod) {
        int killGracePeriodInSeconds = (gracePeriod + 500) / 1000;
        if (gracePeriod != 0 && killGracePeriodInSeconds == 0) {
            log.warn("A kill grace period of %d ms leads to no wait at all since its rounded to seconds. " +
                     "Please use at least 500 as value for wait.kill", gracePeriod);
        }

        return killGracePeriodInSeconds;
    }

    private void removeContainer(ContainerTracker.ContainerShutdownDescriptor descriptor, boolean removeVolumes, String containerId)
        throws DockerAccessException {
        int shutdownGracePeriod = descriptor.getShutdownGracePeriod();
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
        } catch (ExecutionException e) {
            if (e.getCause() instanceof DockerAccessException) {
                throw (DockerAccessException) e.getCause();
            } else {
                throw new DockerAccessException(e, "failed to stop container id [%s]", containerId);
            }
        } catch (WaitTimeoutException e) {
            waited = e.getWaited();
            log.warn("Stop container id [%s] timed out after %s ms", containerId, waited);
        }

        return waited;
    }

    /**
     * Creates a Volume if a volume is referred to during startup in bind mount mapping and
     * a VolumeConfiguration exists
     *
     * @param hub Service hub
     * @param binds volume binds present in ImageConfiguration
     * @param volumes VolumeConfigs present
     * @return List of volumes created
     * @throws DockerAccessException
     */
    public List<String> createVolumesAsPerVolumeBinds(ServiceHub hub, List<String> binds, List<VolumeConfiguration> volumes)
            throws DockerAccessException {

        Map<String, Integer> indexMap = new HashMap<>();
        List<String> volumesCreated = new ArrayList<>();

        for (int index = 0; index < volumes.size(); index++) {
            indexMap.put(volumes.get(index).getName(), index);
        }

        for (String bind : binds) {
            if (bind.contains(":")) {
                String name = bind.substring(0, bind.indexOf(':'));
                Integer volumeConfigIndex = indexMap.get(name);
                if (volumeConfigIndex != null) {
                    VolumeConfiguration volumeConfig = volumes.get(volumeConfigIndex);
                    hub.getVolumeService().createVolume(volumeConfig);
                    volumesCreated.add(volumeConfig.getName());
                }
            }
        }

        return volumesCreated;
    }
}
