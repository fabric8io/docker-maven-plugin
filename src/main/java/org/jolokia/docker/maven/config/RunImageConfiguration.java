package org.jolokia.docker.maven.config;

import java.util.List;
import java.util.Map;

/**
 * @author roland
 * @since 02.09.14
 */
public class RunImageConfiguration {

    public static final RunImageConfiguration DEFAULT = new RunImageConfiguration();

    // Environment variables to set when starting the container. key: variable name, value: env value
    /**
     * @parameter
     */
    private Map<String,String> env;

    // Command to execute in container
    /**
     * @parameter
     */
    private String command;

    // Path to a file where the dynamically mapped properties are written to
    /**
     * @parameter
     */
    private String portPropertyFile;

    // Port mapping. Can contain symbolic names in which case dynamic
    // ports are used
    /**
     * @parameter
     */
    private List<String> ports;

    // Mount volumes from the given image's started containers
    /**
     * @parameter
     */
    private List<String> volumes;

    // Links to other container started
    /**
     * @parameter
     */
    private List<String> links;

    // Wait that many milliseconds after starting the container in order to allow the
    // container to warm up
    /**
     * @parameter
     */
    private WaitConfiguration wait;

    public RunImageConfiguration() {}

    RunImageConfiguration(Map<String, String> env, String command, String portPropertyFile,
                          List<String> ports, List<String> volumes, List<String> links,
                          WaitConfiguration wait) {
        this.env = env;
        this.command = command;
        this.portPropertyFile = portPropertyFile;
        this.ports = ports;
        this.volumes = volumes;
        this.links = links;
        this.wait = wait;
    }

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

    // ======================================================================================

    public static class Builder {
        private Map<String, String> env;
        private String command;
        private String portPropertyFile;
        private List<String> ports;
        private List<String> volumes;
        private List<String> links;
        private WaitConfiguration wait;

        public Builder env(Map<String, String> env) {
            this.env = env;
            return this;
        }

        public Builder command(String command) {
            this.command = command;
            return this;
        }

        public Builder portPropertyFile(String portPropertyFile) {
            this.portPropertyFile = portPropertyFile;
            return this;
        }

        public Builder ports(List<String> ports) {
            this.ports = ports;
            return this;
        }

        public Builder volumes(List<String> volumes) {
            this.volumes = volumes;
            return this;
        }

        public Builder links(List<String> links) {
            this.links = links;
            return this;
        }

        public Builder wait(WaitConfiguration wait) {
            this.wait = wait;
            return this;
        }

        public RunImageConfiguration build() {
            return new RunImageConfiguration(env, command, portPropertyFile, ports,
                                             volumes, links, wait);
        }
    }
}
