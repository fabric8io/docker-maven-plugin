package org.jolokia.docker.maven.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author roland
 * @since 02.09.14
 */
public class BuildImageConfiguration {

    // Base Image name of the data image to use.
    /**
     * @parameter
     */
    private String from;

    /**
     * @parameter
     */
    private String registry;

    /**
     * @parameter
     */
    private List<String> ports;

    /**
     * @paramter
     */
    private List<String> volumes;

    /**
     * @paramter
     */
    private List<String> tags;
    
    /**
     * @parameter
     */
     private Map<String,String> env;

    /**
     * @parameter
     */
    private String command;

    /**
     * @parameter
     */
    private AssemblyConfiguration assembly;
    
    public BuildImageConfiguration() {}

    public String getFrom() {
        return from;
    }

    public String getRegistry() {
        return registry;
    }

    public AssemblyConfiguration getAssemblyConfiguration() {
        return assembly;
    }

    public List<String> getPorts() {
        return ports;
    }

    public List<String> getVolumes() {
        return volumes != null ? volumes : Collections.<String>emptyList();
    }

    public List<String> getTags() {
        return tags != null ? tags : Collections.<String>emptyList();
    }

    public Map<String, String> getEnv() {
        return env;
    }
    
    public String getCommand() {
        return command;
    }

    public static class Builder {
        private final BuildImageConfiguration config = new BuildImageConfiguration();
        
        public Builder from(String from) {
            config.from = from;
            return this;
        }

        public Builder registry(String registry) {
            config.registry = registry;
            return this;
        }

        public Builder assembly(AssemblyConfiguration assembly) {
            config.assembly = assembly;
            return this;
        }
        
        public Builder ports(List<String> ports) {
            config.ports = ports;
            return this;
        }
        
        public Builder volumes(List<String> volumes) {
            config.volumes = volumes;
            return this;
        }
        
        public Builder tags(List<String> tags) {
            config.tags = tags;
            return this;
        }

        public Builder env(Map<String, String> env) {
            config.env = env;
            return this;
        }

        public Builder command(String command) {
            config.command = command;
            return this;
        }

        public BuildImageConfiguration build() {
            return config;
        }
    }
}
