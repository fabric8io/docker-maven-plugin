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

import java.io.*;
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

    // Name of the image to use, including potential tag
    @Parameter(property = "docker.image", required = true)
    private String image;

    // Port mapping. Can contain symbolic names in which case dynamic
    // ports are used
    @Parameter
    private List ports;

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

    /** {@inheritDoc} */
    public void doExecute(DockerAccess access) throws MojoExecutionException {

        checkImage(access);

        PortMapping mappedPorts = parsePorts(ports);
        String containerId = access.createContainer(image,mappedPorts.getExposedPorts(),command);
        info("Created container " + containerId.substring(0,12) + " from image " + image);
        access.startContainer(containerId,mappedPorts.getPortsMap());

        // Remember id for later stopping the container
        registerContainerId(image, containerId);

        // Set maven properties for dynamically assigned ports.
        if (mappedPorts.containsDynamicPorts()) {
            Map<Integer,Integer> realPortMapping = access.queryContainerPortMapping(containerId);
            propagatePortVariables(realPortMapping, mappedPorts);
        }

        // Wait if requested
        waitIfRequested();
    }

    private void waitIfRequested() {
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                // ...
            }
        }
    }

    private void checkImage(DockerAccess access) throws MojoExecutionException {
        if (!access.hasImage(image)) {
            if (autoPull) {
                access.pullImage(image);
            } else {
                throw new MojoExecutionException(this, "No image '" + image + "' found",
                                                 "Please enable 'autoPull' or pull image '" + image +
                                                 "' yourself (docker pull " + image + ")");
            }
        }
    }

    // Store dynamically mapped ports
    private void propagatePortVariables(Map<Integer,Integer> realPortMapping,
                                        PortMapping mappedPorts) throws MojoExecutionException {
        Properties props = new Properties();
        for (Integer containerPort : realPortMapping.keySet()) {
            String var = mappedPorts.getVariableForPort(containerPort);
            if (var != null) {
                String val = "" + realPortMapping.get(containerPort);
                project.getProperties().setProperty(var,val);
                props.setProperty(var,val);
            }
        }

        // However, this can be to late since properties in pom.xml are resolved during the "validate" phase
        // (and we are running later probably in "pre-integration" phase. So, in order to bring the dyamically
        // assigned ports to the integration tests a properties file is written. Not nice, but works. Blame it
        // to maven to not allow late evaluation or any other easy way to inter-plugin communication
        if (portPropertyFile != null) {
            File propFile = new File(portPropertyFile);
            OutputStream os = null;
            try {
                os = new FileOutputStream(propFile);
                props.store(os,"Docker ports");
            } catch (IOException e) {
                throw new MojoExecutionException("Cannot write properties to " + portPropertyFile + ": " + e,e);
            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e) {
                        // best try ...
                    }
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
            if (portsMap == null || varMap == null) {
                throw new IllegalArgumentException("Arguments must not be null");
            }
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

        public Set<Integer> getExposedPorts() {
            return portsMap.keySet();
        }
    }
}
