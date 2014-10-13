package org.jolokia.docker.maven;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.*;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.util.EnvUtil;
import org.jolokia.docker.maven.util.PortMapping;

/**
 * Goal for creating and starting a docker container
 *
 * @author roland
 */
@Mojo(name = "start", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
public class StartMojo extends AbstractDockerMojo {

    @Parameter(defaultValue = "true")
    private boolean autoPull;

    // Map holding associations between started containers and their images
    private Map<String, String> containerImageMap = new HashMap<>();

    /** {@inheritDoc} */
    public void executeInternal(DockerAccess docker) throws MojoExecutionException, MojoFailureException {

        for (ImageConfiguration imageConfig : getRunImageConfigsInOrder()) {
            String image = imageConfig.getName();
            checkImage(docker,image);

            RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
            if (runConfig == null) {
                // It's a data image which needs not to have a runtime configuration
                runConfig = RunImageConfiguration.DEFAULT;
            }
            PortMapping mappedPorts = new PortMapping(runConfig.getPorts(),project.getProperties());
            String container = docker.createContainer(image,
                                                      mappedPorts.getContainerPorts(),
                                                      runConfig.getCommand(),
                                                      runConfig.getEnv());
            List<String> containersHoldingVolumes = extractVolumesFrom(runConfig);
            docker.startContainer(container, mappedPorts.getPortsMap(), containersHoldingVolumes);
            containerImageMap.put(image,container);
            info("Created and started container " + container.substring(0, 12) + " from image " + image);

            // Remember id for later stopping the container
            registerShutdownAction(new ShutdownAction(image,container));

            // Set maven properties for dynamically assigned ports.
            if (mappedPorts.containsDynamicPorts()) {
                mappedPorts.updateVarsForDynamicPorts(docker.queryContainerPortMapping(container));
                propagatePortVariables(mappedPorts,runConfig.getPortPropertyFile());
            }

            // Wait if requested
            waitIfRequested(runConfig,mappedPorts);
        }

        /*
        String container,dataImage,dataContainer;

        if (useDataContainer()) {
            dataImage = createDataImage(docker);
            if (mergeData) {
                // Image created on the fly and used for action
                dataContainer = null;
                container = docker.createContainer(dataImage,mappedPorts.getContainerPorts(),command,env);
            } else {
                dataContainer = docker.createContainer(dataImage, null, null, env);
                docker.startContainer(dataContainer, null, null);

                container = docker.createContainer(image,mappedPorts.getContainerPorts(),command,env);
            }
        } else {
            dataImage = null;
            dataContainer = null;

            container = docker.createContainer(image,mappedPorts.getContainerPorts(),command,env);
        }
        */

    }

    private List<String> extractVolumesFrom(RunImageConfiguration runConfig) throws MojoFailureException {
        List<String> volumesFromImages = runConfig.getVolumesFrom();
        List<String> containers = new ArrayList<>();
        if (volumesFromImages != null) {
            for (String volumesFrom : volumesFromImages) {
                String container = containerImageMap.get(volumesFrom);
                if (container  == null) {
                    throw new MojoFailureException("No container for image " + volumesFrom + " started.");
                }
                containers.add(container);
            }
        }
        return containers;
    }

    // Check images for volume / link dependencies and return it in the right order.
    // Only return images which should be run
    // Images references via volumes but with no run configuration are started once to create
    // an appropriate container which can be linked into the image
    private List<ImageConfiguration> getRunImageConfigsInOrder() throws MojoFailureException {
        List<ImageConfiguration> ret = new ArrayList<>();
        List<ImageConfiguration> secondPass = new ArrayList<>();
        Set<String> processedImageNames = new HashSet<>();

        // First pass: Pick all data images and all without dependencies
        for (ImageConfiguration config : images) {
            RunImageConfiguration runConfig = config.getRunConfiguration();
            if (runConfig == null || runConfig.getVolumesFrom() == null || runConfig.getVolumesFrom().size() == 0) {
                // A data image only or no dependency. Add it to the list of data image which can be always
                // created first.
                ret.add(imageProcessed(processedImageNames, config));
            } else {
                secondPass.add(config);
            }
        }

        // Next passes: Those with dependencies are checked whether they already have been visited.
        List<ImageConfiguration> remaining = secondPass;
        int retries = 10;
        do {
            remaining = resolveImageDependencies(ret,remaining,processedImageNames);
        } while (remaining.size() > 0  && retries-- > 0);
        if (retries == 0 && remaining.size() > 0) {
            error("Cannot resolve image dependencies for volumes after 10 passes");
            error("Unresolved images:");
            for (ImageConfiguration config : remaining) {
                error("     " + config.getName());
            }
            throw new MojoFailureException("Invalid image configuration for volumesFrom: Cyclic dependencies");
        }
        return ret;
    }

    private List<ImageConfiguration> resolveImageDependencies(List<ImageConfiguration> ret,
                                                              List<ImageConfiguration> secondPass,
                                                              Set<String> processedImageNames) {
        List<ImageConfiguration> nextPass = new ArrayList<>();
        for (ImageConfiguration config : secondPass) {
            List<String> volumesFrom = config.getRunConfiguration().getVolumesFrom();
            if (allDependentImagesProcessed(processedImageNames, volumesFrom)) {
                ret.add(imageProcessed(processedImageNames, config));
            } else {
                // Still unresolved dependencies
                nextPass.add(config);
            }
        }
        return nextPass;
    }

    private ImageConfiguration imageProcessed(Set<String> processedImageNames, ImageConfiguration config) {
        processedImageNames.add(config.getName());
        return config;
    }

    private boolean allDependentImagesProcessed(Set<String> dataImages, List<String> volumesFrom) {
        for (String volumeFrom : volumesFrom) {
            if (!dataImages.contains(volumeFrom)) {
                return false;
            }
        }
        return true;
    }


    // ========================================================================================================

    public void checkImage(DockerAccess docker,String image) throws MojoExecutionException, MojoFailureException {
        if (!docker.hasImage(image)) {
            if (autoPull) {
                docker.pullImage(image,prepareAuthConfig(image));
            } else {
                throw new MojoExecutionException(this, "No image '" + image + "' found",
                                                 "Please enable 'autoPull' or pull image '" + image +
                                                 "' yourself (docker pull " + image + ")");
            }
        }
    }

    private void waitIfRequested(RunImageConfiguration runConfig, PortMapping mappedPorts) {
        WaitConfiguration wait = runConfig.getWaitConfiguration();
        if (wait != null) {
            String waitHttp = wait.getUrl();
            if (waitHttp != null) {
                String waitUrl = mappedPorts.replaceVars(waitHttp);
                long waited = EnvUtil.httpPingWait(waitUrl, wait.getTime());
                info("Waited on " + waitUrl + " for " + waited + " ms");
            } else if (wait.getTime() > 0) {
                EnvUtil.sleep(wait.getTime());
                info("Waited " + wait + " ms");
            }
        }
    }

    // Store dynamically mapped ports
    private void propagatePortVariables(PortMapping mappedPorts,String portPropertyFile) throws MojoExecutionException {
        Properties props = new Properties();
        Map<String,Integer> dynamicPorts = mappedPorts.getDynamicPorts();
        for (Map.Entry<String,Integer> entry : dynamicPorts.entrySet()) {
            String var = entry.getKey();
            String val = "" + entry.getValue();
            project.getProperties().setProperty(var,val);
            props.setProperty(var,val);
        }

        // However, this can be to late since properties in pom.xml are resolved during the "validate" phase
        // (and we are running later probably in "pre-integration" phase. So, in order to bring the dynamically
        // assigned ports to the integration tests a properties file is written. Not nice, but works. Blame it
        // to maven to not allow late evaluation or any other easy way to inter-plugin communication
        if (portPropertyFile != null) {
            EnvUtil.writePortProperties(props, portPropertyFile);
        }
    }
}
