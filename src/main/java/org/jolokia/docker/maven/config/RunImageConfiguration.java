package org.jolokia.docker.maven.config;

import java.util.List;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author roland
 * @since 02.09.14
 */
public class RunImageConfiguration {

    public static final RunImageConfiguration DEFAULT = new RunImageConfiguration();

    // Environment variables to set when starting the container. key: variable name, value: env value
    @Parameter
    private Map<String,String> env;

    // Command to execute in container
    @Parameter
    private String command;

    // Path to a file where the dynamically mapped properties are written to
    @Parameter
    private String portPropertyFile;

    // Port mapping. Can contain symbolic names in which case dynamic
    // ports are used
    @Parameter
    private List<String> ports;

    // Mount volumes from the given image's started containers
    @Parameter
    private List<String> volumes;

    // Links to other container started
    @Parameter
    private List<String> links;

    // Wait that many milliseconds after starting the container in order to allow the
    // container to warm up
    @Parameter
    private WaitConfiguration wait;

    public Map<String, String> getEnv() {
        return env;
    }

    public List<String> getPorts() {
        return ports;
    }

    public String getCommand() {
        return command;
    }

    public String getPortPropertyFile() {
        return portPropertyFile;
    }

    public WaitConfiguration getWaitConfiguration() {
        return wait;
    }

    public List<String> getVolumesFrom() {
        return volumes;
    }

    public List<String> getLinks() {
        return links;
    }
}
