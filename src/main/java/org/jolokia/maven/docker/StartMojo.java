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

    public void doExecute() throws MojoExecutionException {
        DockerAccess access = createDockerAccess();

        if (!access.hasImage(image)) {
            if (autoPull) {
                error("Autopull not implemented yet, please pull image '" + image + "' yourself (docker pull " + image + ")");
            }
            throw new MojoExecutionException(this,"No image '" + image + "' found","Please pull image '" + image +
                                                                                   "' yourself (docker pull " + image + ")");
        }
        PortMapping mappedPorts = parsePorts(ports);
        String containerId = access.createContainer(image,mappedPorts.getPortsMap(),command);
        info(">>> Docker - Created container " + containerId.substring(0,12) + " from image " + image);
        access.startContainer(containerId,mappedPorts.getPortsMap());

        // Set id for later stopping the container
        project.getProperties().setProperty(PROPERTY_CONTAINER_ID,containerId);

        // Set maven variables for dynamically assigned ports.
        if (mappedPorts.containsDynamicPorts()) {
            Map<Integer,Integer> realPortMapping = access.getContainerPortMapping(containerId);
            for (Integer containerPort : realPortMapping.keySet()) {
                String var = mappedPorts.getVariableForPort(containerPort);
                if (var != null) {
                    project.getProperties().setProperty(var,"" + realPortMapping.get(containerPort));
                }
            }
        }

    }

    private PortMapping parsePorts(List<String> ports) throws MojoExecutionException {
        Map<Integer,Integer> ret = new HashMap<Integer, Integer>();
        Map<Integer,String> varMap = new HashMap<Integer, String>();
        if (ports != null) {
            for (String port : ports) {
                try {
                    String ps[] = port.split(":", 2);
                    Integer containerPort = Integer.parseInt(ps[1]);
                    Integer hostPort;
                    try {
                        hostPort = Integer.parseInt(ps[0]);
                    } catch (NumberFormatException exp) {
                        // Port should be dynamically assigned and set to the variable give in ps[0]
                        hostPort = null;
                        varMap.put(containerPort,ps[0]);
                    }
                    ret.put(containerPort, hostPort);
                } catch (NumberFormatException exp) {
                    throw new MojoExecutionException("Port mappings must be given in the format <hostPort>:<mappedPort> (e.g. 8080:8080). " +
                                                     "The given config '" + port + "' doesn't match this");
                }
            }
        }
        return new PortMapping(ret,varMap);
    }

    private static class PortMapping {
        private final Map<Integer, String> varMap;
        private final Map<Integer, Integer> portsMap;

        PortMapping(Map<Integer, Integer> portsMap, Map<Integer, String> varMap) {
            this.portsMap = portsMap;
            this.varMap = varMap;
        }

        Map<Integer, Integer> getPortsMap() {
            return portsMap;
        }

        boolean containsDynamicPorts() {
            return varMap.size() > 0;
        }

        String getVariableForPort(Integer containerPort) {
            return varMap.get(containerPort);
        }
    }
}
