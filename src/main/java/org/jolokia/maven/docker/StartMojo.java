package org.jolokia.maven.docker;

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
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

/**
 * Goal for creating and starting a docker container
 *
 * @author roland
 */
@Mojo(name = "start", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
public class StartMojo extends AbstractDockerMojo {

    @Component
    MavenProject project;

    @Parameter(property = "docker.image", required = true)
    private String image;

    @Parameter
    private List ports;

    @Parameter(property = "docker.autoPull", defaultValue = "true")
    private boolean autoPull;

    @Parameter(property = "docker.tag")
    private String tag;

    @Parameter(property = "docker.registry")
    private String registry;

    @Parameter(property = "docker.command")
    private String command;

    public void execute() throws MojoExecutionException {
        DockerAccess access = createDockerAccess();

        if (!access.hasImage(image)) {
            if (autoPull) {
                error("Autopull not implemented yet, please pull image '" + image + "' yourself (docker pull " + image + ")");
            }
            throw new MojoExecutionException(this,"No image '" + image + "' found","Please pull image '" + image +
                                                                                   "' yourself (docker pull " + image + ")");
        }
        Map<Integer,Integer> mappedPorts = parsePorts(ports);
        String containerId = access.createContainer(image,mappedPorts,command);
        info(">>> Docker - Created container " + containerId + " from image " + image);
        access.startContainer(containerId,mappedPorts);
        project.getProperties().setProperty(PROPERTY_CONTAINER_ID,containerId);

    }

    private Map<Integer,Integer> parsePorts(List<String> ports) throws MojoExecutionException {
        Map<Integer,Integer> ret = new HashMap<Integer, Integer>();
        if (ports != null) {
            for (String port : ports) {
                try {
                    String ps[] = port.split(":", 2);
                    Integer hostPort = Integer.parseInt(ps[0]);
                    Integer containerPort = Integer.parseInt(ps[1]);
                    ret.put(containerPort, hostPort);
                } catch (NumberFormatException exp) {
                    throw new MojoExecutionException("Port mappings must be given in the format <hostPort>:<mappedPort> (e.g. 8080:8080). " +
                                                     "The given config '" + port + "' doesn't match this");
                }
            }
        }
        return ret;
    }
}
