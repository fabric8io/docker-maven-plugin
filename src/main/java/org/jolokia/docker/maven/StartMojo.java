package org.jolokia.docker.maven;

/*
 * Copyright 2009-2014 Roland Huss Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by
 * applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.StringUtils;
import org.jolokia.docker.maven.access.*;
import org.jolokia.docker.maven.access.log.LogCallback;
import org.jolokia.docker.maven.access.log.LogGetHandle;
import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.log.LogDispatcher;
import org.jolokia.docker.maven.util.*;


/**
 * Goal for creating and starting a docker container. This goal evaluates the image configuration
 *
 * @author roland
 * @goal start
 * @phase pre-integration-test
 */
public class StartMojo extends AbstractDockerMojo {

    /** @parameter property = "docker.showLog" */
    private String showLog;

    // Map holding associations between started containers and their images via name and aliases
    // Key: Image, Value: Container
    private Map<String, String> containerImageNameMap = new HashMap<>();

    // Key: Alias, Value: Image
    private Map<String, String> imageAliasMap = new HashMap<>();
    
    /** @parameter property = "docker.watch.enabled" default-value = "false" */
    protected boolean watchEnabled;

    /** @parameter property = "docker.watch.interval" default-value = "5000" */
    protected Integer watchInterval;

    /** {@inheritDoc} */
    @Override
    public void executeInternal(DockerAccess docker) throws DockerAccessException, MojoExecutionException {

        getPluginContext().put(CONTEXT_KEY_START_CALLED, true);

        LogDispatcher dispatcher = getLogDispatcher(docker);

        for (StartOrderResolver.Resolvable resolvable : getImagesConfigsInOrder()) {
            final ImageConfiguration imageConfig = (ImageConfiguration) resolvable;

            // Still to check: How to work with linking, volumes, etc ....
            //String imageName = new ImageName(imageConfig.getName()).getFullNameWithTag(registry);

            String imageName = imageConfig.getName();

            checkImageWithAutoPull(docker, imageName, getRegistry(imageConfig));

            RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
            PortMapping mappedPorts = getPortMapping(runConfig, project.getProperties());

            ContainerCreateConfig config = createContainerConfig(docker, imageName, runConfig, mappedPorts);
            String containerId = docker.createContainer(config);
            docker.startContainer(containerId);

            if (showLog(imageConfig)) {
                dispatcher.trackContainerLog(containerId, getContainerLogSpec(containerId, imageConfig));
            }
            registerContainer(containerId, imageConfig);
            log.info("Created and started container " + toContainerAndImageDescription(containerId, imageConfig.getDescription()));

            // Remember id for later stopping the container
            registerShutdownAction(new ShutdownAction(imageConfig, containerId));

            // Set maven properties for dynamically assigned ports.
            if (mappedPorts.containsDynamicPorts()) {
                mappedPorts.updateVariablesWithDynamicPorts(docker.queryContainerPortMapping(containerId));
                propagatePortVariables(mappedPorts, runConfig.getPortPropertyFile());
            }

            // Wait if requested
            waitIfRequested(runConfig, mappedPorts, docker, containerId);
            watchIfRequested(runConfig, mappedPorts, docker, containerId, imageName);
        }
    }

    // visible for testing
    ContainerCreateConfig createContainerConfig(DockerAccess docker, String imageName, RunImageConfiguration runConfig, PortMapping mappedPorts)
            throws MojoExecutionException, DockerAccessException {
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
                    .environment(runConfig.getEnv())
                    .command(runConfig.getCommand())
                    .hostConfig(createContainerHostConfig(docker, runConfig, mappedPorts));
            VolumeConfiguration volumeConfig = runConfig.getVolumeConfiguration();
            if (volumeConfig != null) {
                config.binds(volumeConfig.getBind());
            }
            return config;
        }
        catch (IllegalArgumentException e) {
            throw new MojoExecutionException(String.format("Failed to create contained configuration for [%s]", imageName), e);
        }
    }

    ContainerHostConfig createContainerHostConfig(DockerAccess docker, RunImageConfiguration runConfig, PortMapping mappedPorts)
            throws MojoExecutionException, DockerAccessException {
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

    // visible for testing
    PortMapping getPortMapping(RunImageConfiguration runConfig, Properties properties) throws MojoExecutionException {
        try {
            return new PortMapping(runConfig.getPorts(), properties);
        }
        catch (IllegalArgumentException exp) {
            throw new MojoExecutionException("Cannot parse port mapping", exp);
        }
    }

    private List<StartOrderResolver.Resolvable> getImagesConfigsInOrder() throws MojoExecutionException {
        try {
            return StartOrderResolver.resolve(convertToResolvables(getImages()));
        }
        catch (MojoExecutionException e) {
            log.error(e.getMessage());
            throw new MojoExecutionException("No container start order could be found", e);
        }
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
    List<String> findContainersForImages(List<String> images) throws MojoExecutionException {
        if (images != null) {
            List<String> containers = new ArrayList<>();
            for (String image : images) {
                String container = lookupContainer(image);
                if (container == null) {
                    throw new MojoExecutionException("No container for image " + image + " started.");
                }
                containers.add(container);
            }
            return containers;
        }
        return null;
    }

    private String lookupContainer(String lookup) {
        String image = imageAliasMap.containsKey(lookup) ? imageAliasMap.get(lookup) : lookup;
        return containerImageNameMap.get(image);
    }

    void registerContainer(String container, ImageConfiguration imageConfig) {
        containerImageNameMap.put(imageConfig.getName(), container);
        if (imageConfig.getAlias() != null) {
            imageAliasMap.put(imageConfig.getAlias(), imageConfig.getName());
        }
    }

    private List<StartOrderResolver.Resolvable> convertToResolvables(List<ImageConfiguration> images) {
        List<StartOrderResolver.Resolvable> ret = new ArrayList<>();
        for (ImageConfiguration config : images) {
            ret.add(config);
        }
        return ret;
    }

    // ========================================================================================================

    private void watchIfRequested(final RunImageConfiguration runConfig, final PortMapping mappedPorts, final DockerAccess docker, final String initialContainerId, final String imageName) throws DockerAccessException {
        WatchConfiguration watch = runConfig.getWatchConfiguration();
        
        final AtomicReference<String> imageId = new AtomicReference<>();
        final AtomicReference<String> containerId = new AtomicReference<>(initialContainerId);

        imageId.set(docker.getImageId(imageName));
        boolean keepRunning = true;
        if (watch != null || watchEnabled) {
            watch = watch != null ? watch : new WatchConfiguration.Builder().time(watchInterval).build();
            while (keepRunning && !Thread.interrupted()) {
                try {
                    String currentImageId = docker.getImageId(imageName);
                    String oldValue = imageId.getAndSet(currentImageId);
                    if (!currentImageId.equals(oldValue)) {
                        log.info("Image: "+imageName+" has been updated. Recreating container:"+containerId.get());
                        log.info("Stopping container:"+containerId.get());
                        docker.stopContainer(containerId.get());
                        ContainerCreateConfig config = createContainerConfig(docker, imageName, runConfig, mappedPorts);
                        containerId.set(docker.createContainer(config));
                        log.info("Starting container:"+containerId.get());
                        docker.startContainer(containerId.get());
                    }
                    Thread.sleep(watch.getInterval());
                } catch (DockerAccessException e) {
                    log.warn("Error while watching for image:" + imageName);
                } catch (MojoExecutionException e) {
                    log.warn("Error while recreating container for image:" + imageName);
                    keepRunning = false;
                } catch (InterruptedException e) {
                    log.warn("Interrupted while watching for image:" + imageName);
                    keepRunning = false;
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    
    private void waitIfRequested(RunImageConfiguration runConfig, PortMapping mappedPorts, DockerAccess docker, String containerId) {
        WaitConfiguration wait = runConfig.getWaitConfiguration();
        if (wait != null) {
            ArrayList<WaitUtil.WaitChecker> checkers = new ArrayList<>();
            ArrayList<String> logOut = new ArrayList<>();
            if (wait.getUrl() != null) {
                String waitUrl = mappedPorts.replaceVars(wait.getUrl());
                checkers.add(new WaitUtil.HttpPingChecker(waitUrl));
                logOut.add("on url " + waitUrl);
            }
            if (wait.getLog() != null) {
                checkers.add(getLogWaitChecker(wait.getLog(), docker, containerId));
                logOut.add("on log out '" + wait.getLog() + "'");
            }
            long waited = WaitUtil.wait(wait.getTime(), checkers.toArray(new WaitUtil.WaitChecker[0]));
            log.info("Waited " + StringUtils.join(logOut.toArray(), " and ") + waited + " ms");
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
    private void propagatePortVariables(PortMapping mappedPorts, String portPropertyFile) throws MojoExecutionException {
        Properties props = new Properties();
        Map<String, Integer> dynamicPorts = mappedPorts.getPortVariables();
        for (Map.Entry<String, Integer> entry : dynamicPorts.entrySet()) {
            String var = entry.getKey();
            String val = "" + entry.getValue();
            project.getProperties().setProperty(var, val);
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

    protected boolean showLog(ImageConfiguration imageConfig) {
        if (showLog != null) {
            if (showLog.equalsIgnoreCase("true")) {
                return true;
            }
            else if (showLog.equalsIgnoreCase("false")) {
                return false;
            }
            else {
                return matchesConfiguredImages(showLog, imageConfig);
            }
        }

        RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
        if (runConfig != null) {
            LogConfiguration logConfig = runConfig.getLog();
            if (logConfig != null) {
                return logConfig.isEnabled();
            }
        }
        return false;
    }
}
