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

import java.io.File;
import java.util.*;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.jolokia.docker.maven.assembly.DockerArchiveCreator;
import org.jolokia.docker.maven.util.*;

/**
 * Goal for creating and starting a docker container
 *
 * @author roland
 */
@Mojo(name = "start", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
public class StartMojo extends AbstractDockerMojo {

    // Current maven project
    @Component
    private MavenProject project;

    // Name of the image to use, including potential tag
    @Parameter(property = "docker.image", required = true)
    private String image;

    // Port mapping. Can contain symbolic names in which case dynamic
    // ports are used
    @Parameter
    private List<String> ports;

    // Whether to pull an image if not yet locally available (not implemented yet)
    @Parameter(property = "docker.autoPull", defaultValue = "true")
    private boolean autoPull;

    // Command to execute in contained
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

    /**
     * A descriptor to use for building the data assembly to be exported
     * in an Docker image
     */
    @Parameter
    private String assemblyDescriptor;

    /**
     * Reference to an assembly descriptor included.
     */
    @Parameter
    private String assemblyDescriptorRef;

    @Component
    private DockerArchiveCreator dockerArchiveCreator;

    /** {@inheritDoc} */
    public void executeInternal(DockerAccess docker) throws MojoExecutionException, MojoFailureException {
        checkImage(docker);

        PortMapping mappedPorts = new PortMapping(ports,project.getProperties());
        String dataImage = getDataImageName();
        String dataContainerId = createDataContainer(docker,dataImage);

        String containerId = docker.createContainer(image,mappedPorts.getContainerPorts(),command);
        info("Created container " + containerId.substring(0, 12) + " from image " + image);
        docker.startContainer(containerId, mappedPorts.getPortsMap(),dataContainerId);

        // Remember id for later stopping the container
        registerStartData(image, containerId, dataImage, dataContainerId);

        // Set maven properties for dynamically assigned ports.
        if (mappedPorts.containsDynamicPorts()) {
            mappedPorts.updateVarsForDynamicPorts(docker.queryContainerPortMapping(containerId));
            propagatePortVariables(mappedPorts);
        }

        // Wait if requested
        waitIfRequested(mappedPorts);
    }



    // ========================================================================================================

    // Create a data container and return its ID. Return the ID or null if no data containe is created
    private String createDataContainer(DockerAccess docker, String dataImage) throws MojoFailureException, MojoExecutionException {
        if (assemblyDescriptor != null || assemblyDescriptorRef != null) {
            String dataContainerId = null;
            if (assemblyDescriptor != null && assemblyDescriptorRef != null) {
                throw new MojoFailureException("Either a assemblyDescriptor '" + assemblyDescriptor +
                                               "' or a assemblyDescriptorRef '" + assemblyDescriptorRef +
                                               "' can be used");
            }
            MojoParameters params =
                    new MojoParameters(session, project, archive, mavenFileFilter);

            File dockerArchive = dockerArchiveCreator.create(params, assemblyDescriptor, assemblyDescriptorRef);
            info("Created docker archive " + dockerArchive);
            docker.buildImage(dataImage,dockerArchive);
            dataContainerId = docker.createContainer(dataImage,null,null);
            docker.startContainer(dataContainerId,null,null);
            return dataContainerId;
        }
        return null;
    }

    // Data image
    private String getDataImageName() {
        return project.getGroupId() + "/" + project.getArtifactId() + ":" + project.getVersion();
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

    // =======================================================================================================
    // ==============================================================================================================
    // Parameters required from Maven when building an assembly. They cannot be injected directly
    // into DockerAssemblyCreator.
    // See also here: http://maven.40175.n5.nabble.com/Mojo-Java-1-5-Component-MavenProject-returns-null-vs-JavaDoc-parameter-expression-quot-project-quot-s-td5733805.html
    @Parameter
    private MavenArchiveConfiguration archive;
    @Component
    private MavenSession session;
    @Component
    private MavenFileFilter mavenFileFilter;

}
