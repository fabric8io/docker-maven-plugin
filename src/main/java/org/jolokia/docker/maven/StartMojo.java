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
import org.jolokia.docker.maven.util.*;

/**
 * Goal for creating and starting a docker container
 *
 * @author roland
 */
@Mojo(name = "start", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
public class StartMojo extends AbstractDockerMojo {

    @Parameter(defaultValue = "true")
    private boolean autoPull;

    // Map holding associations between started containers and their images via name and aliases
    // Key: Image, Value: Container
    private Map<String, String> containerImageNameMap = new HashMap<>();

    // Key: Alias, Value: Image
    private Map<String, String> imageAliasMap = new HashMap<>();

    /** {@inheritDoc} */
    public void executeInternal(DockerAccess docker) throws MojoExecutionException, MojoFailureException {

        for (StartOrderResolver.Resolvable resolvable : StartOrderResolver.resolve(convertToResolvables(images))) {
            ImageConfiguration imageConfig = (ImageConfiguration) resolvable;
            String imageName = imageConfig.getName();
            checkImage(docker,imageName);

            RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
            if (runConfig == null) {
                // It's a data image which needs not to have a runtime configuration
                runConfig = RunImageConfiguration.DEFAULT;
            }
            PortMapping mappedPorts = new PortMapping(runConfig.getPorts(),project.getProperties());
            String container = docker.createContainer(imageName,
                                                      mappedPorts.getContainerPorts(),
                                                      runConfig.getCommand(),
                                                      runConfig.getEnv());
            docker.startContainer(container,
                                  mappedPorts.getPortsMap(),
                                  findContainersForImages(runConfig.getVolumesFrom()),
                                  findLinksWithContainerNames(docker,runConfig.getLinks()));
            registerContainer(container, imageConfig);
            info("Created and started container " + container.substring(0, 12) + " from image " + imageName);

            // Remember id for later stopping the container
            registerShutdownAction(new ShutdownAction(imageName,container));

            // Set maven properties for dynamically assigned ports.
            if (mappedPorts.containsDynamicPorts()) {
                mappedPorts.updateVarsForDynamicPorts(docker.queryContainerPortMapping(container));
                propagatePortVariables(mappedPorts,runConfig.getPortPropertyFile());
            }

            // Wait if requested
            waitIfRequested(runConfig,mappedPorts);
        }
    }

    private List<String> findLinksWithContainerNames(DockerAccess docker, List<String> links) throws MojoExecutionException {
        List<String> ret = new ArrayList<>();
        for (String[] link : EnvUtil.splitLinks(links)) {
            String container = lookupContainer(link[0]);
            if (container == null) {
                throw new MojoExecutionException("Cannot find container for " + link[0] + " while preparing links");
            }
            ret.add(docker.getContainerName(container) + ":" + link[1]);
        }
        return ret;
    }

    private List<String> findContainersForImages(List<String> images) throws MojoFailureException {
        List<String> containers = new ArrayList<>();
        if (images != null) {
            for (String image : images) {
                String container = lookupContainer(image);
                if (container  == null) {
                    throw new MojoFailureException("No container for image " + image + " started.");
                }
                containers.add(container);
            }
        }
        return containers;
    }

    private String lookupContainer(String lookup) {
        if (imageAliasMap.containsKey(lookup)) {
            String image = imageAliasMap.get(lookup);
            return containerImageNameMap.get(image);
        }
        if (containerImageNameMap.containsKey(lookup)) {
            return containerImageNameMap.get(lookup);
        }
        return null;
    }

    private void registerContainer(String container, ImageConfiguration imageConfig) {
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
            int time = wait.getTime();
            if (waitHttp != null) {
                String waitUrl = mappedPorts.replaceVars(waitHttp);
                long waited = EnvUtil.httpPingWait(waitUrl, time);
                info("Waited on " + waitUrl + " for " + waited + " ms");
            } else if (time > 0) {
                EnvUtil.sleep(time);
                info("Waited " + time + " ms");
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
