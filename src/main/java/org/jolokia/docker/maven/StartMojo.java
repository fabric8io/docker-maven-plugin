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
import org.jolokia.docker.maven.util.EnvUtil;
import org.jolokia.docker.maven.util.PortMapping;

/**
 * Goal for creating and starting a docker container
 *
 * @author roland
 */
@Mojo(name = "start", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
public class StartMojo extends AbstractDataSupportedDockerMojo {

    // Name of the image to use, including potential tag
    @Parameter(property = "docker.image", required = true)
    private String image;

    // Whether to merge the data in the original image or use a separate data image
    @Parameter(property = "docker.mergeData", required = false, defaultValue = "false")
    private boolean mergeData;

    // Port mapping. Can contain symbolic names in which case dynamic
    // ports are used
    @Parameter
    private List<String> ports;

    // Whether to pull an image if not yet locally available (not implemented yet)
    @Parameter(property = "docker.autoPull", defaultValue = "true")
    private boolean autoPull;

    // Command to execute in container
    @Parameter(property = "docker.command")
    private String command;

    // Path to a file where the dynamically mapped properties are written to
    @Parameter(property = "docker.portPropertyFile")
    private String portPropertyFile;

    // Wait that many milliseconds after starting the container in order to allow the
    // container to warm up
    @Parameter(property = "docker.wait", defaultValue = "0")
    private int wait;

    // Wait until the given URL is accessible
    @Parameter(property = "docker.waitHttp")
    private String waitHttp;

    /** {@inheritDoc} */
    public void executeInternal(DockerAccess docker) throws MojoExecutionException, MojoFailureException {
        checkImage(docker);

        PortMapping mappedPorts = new PortMapping(ports,project.getProperties());

        String container,dataImage,dataContainer;

        if (useDataContainer()) {
            if (mergeData) {
                // Image created on the fly and used for action
                dataImage = createDataImage(image, docker);
                dataContainer = null;

                container = docker.createContainer(dataImage,mappedPorts.getContainerPorts(),command);
            } else {
                dataImage = createDataImage(null, docker);
                dataContainer = docker.createContainer(dataImage, null, null);
                docker.startContainer(dataContainer, null, null);

                container = docker.createContainer(image,mappedPorts.getContainerPorts(),command);
            }
        } else {
            dataImage = null;
            dataContainer = null;
            mergeData = false; // Force to false, doesnt have any effect anyways when not using data container

            container = docker.createContainer(image,mappedPorts.getContainerPorts(),command);
        }

        docker.startContainer(container, mappedPorts.getPortsMap(), dataContainer);
        info("Created and started container " + container.substring(0, 12) + " from image " + (useDataContainer() && mergeData ? dataImage : image));

        // Remember id for later stopping the container
        registerShutdownAction(new ShutdownAction(image,container,dataImage,mergeData));

        // Set maven properties for dynamically assigned ports.
        if (mappedPorts.containsDynamicPorts()) {
            mappedPorts.updateVarsForDynamicPorts(docker.queryContainerPortMapping(container));
            propagatePortVariables(mappedPorts);
        }

        // Wait if requested
        waitIfRequested(mappedPorts);
    }


    // ========================================================================================================

    private boolean useDataContainer() {
        return assemblyDescriptor != null || assemblyDescriptorRef != null;
    }

    private void waitIfRequested(PortMapping mappedPorts) {
        if (waitHttp != null) {
            String waitUrl = mappedPorts.replaceVars(waitHttp);
            long waited = EnvUtil.httpPingWait(waitUrl, wait);
            info("Waited on " + waitUrl + " for " + waited + " ms");
        } else if (wait > 0) {
            EnvUtil.sleep(wait);
            info("Waited " + wait + " ms");
        }
    }


    private void checkImage(DockerAccess docker) throws MojoExecutionException {
        if (!docker.hasImage(image)) {
            if (autoPull) {
                docker.pullImage(image);
            } else {
                throw new MojoExecutionException(this, "No image '" + image + "' found",
                                                 "Please enable 'autoPull' or pull image '" + image +
                                                 "' yourself (docker pull " + image + ")");
            }
        }
    }

    // Store dynamically mapped ports
    private void propagatePortVariables(PortMapping mappedPorts) throws MojoExecutionException {
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
