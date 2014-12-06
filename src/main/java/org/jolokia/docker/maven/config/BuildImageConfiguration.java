package org.jolokia.docker.maven.config;

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
    private String exportDir;

    /**
     * @parameter
     */
    private String registry;

    /**
     * @parameter
     */
    private String assemblyDescriptor;

    /**
     * @parameter
     */
    private String assemblyDescriptorRef;

    /**
     * @parameter
     */
    private List<String> ports;

    /**
     * @paramter
     */
    private List<String> volumes;
    
    /**
     * @parameter
     */
     private Map<String,String> env;

    /**
     * @parameter
     */
    private String command;

    public BuildImageConfiguration() {}
   
    public String getFrom() {
        return from;
    }

    public String getExportDir() {
        return exportDir;
    }

    public String getRegistry() {
        return registry;
    }

    public String getAssemblyDescriptor() {
        return assemblyDescriptor;
    }

    public String getAssemblyDescriptorRef() {
        return assemblyDescriptorRef;
    }

    public List<String> getPorts() {
        return ports;
    }

    public List<String> getVolumes() {
        return volumes;
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

        public Builder exportDir(String exportDir) {
            config.exportDir = exportDir;
            return this;
        }

        public Builder registry(String registry) {
            config.registry = registry;
            return this;
        }

        public Builder assemblyDescriptor(String assemblyDescriptor) {
            config.assemblyDescriptor = assemblyDescriptor;
            return this;
        }

        public Builder assemblyDescriptorRef(String assemblyDescriptorRef) {
            config.assemblyDescriptorRef = assemblyDescriptorRef;
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
