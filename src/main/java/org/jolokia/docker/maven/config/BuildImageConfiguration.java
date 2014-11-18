package org.jolokia.docker.maven.config;

import java.util.List;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author roland
 * @since 02.09.14
 */
public class BuildImageConfiguration {

    // Base Image name of the data image to use.
    @Parameter
    private String from;

    @Parameter
    private String exportDir;

    @Parameter
    private String registry;

    @Parameter
    private String assemblyDescriptor;

    @Parameter
    private String assemblyDescriptorRef;

    @Parameter
    private List<String> ports;

    @Parameter
    private Map<String,String> env;

    @Parameter
    private String command;

    public BuildImageConfiguration() {}

    private BuildImageConfiguration(String from, String exportDir, String registry,
                                    String assemblyDescriptor, String assemblyDescriptorRef,
                                    List<String> ports, Map<String, String> env, String command) {
        this.from = from;
        this.exportDir = exportDir;
        this.registry = registry;
        this.assemblyDescriptor = assemblyDescriptor;
        this.assemblyDescriptorRef = assemblyDescriptorRef;
        this.ports = ports;
        this.env = env;
        this.command = command;
    }

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

    public Map<String, String> getEnv() {
        return env;
    }
    
    public String getCommand() {
        return command;
    }


    public static class Builder {
        private String from;
        private String exportDir;
        private String registry;
        private String assemblyDescriptor;
        private String assemblyDescriptorRef;
        private List<String> ports;
        private Map<String, String> env;
        private String command;

        public Builder from(String from) {
            this.from = from;
            return this;
        }

        public Builder exportDir(String exportDir) {
            this.exportDir = exportDir;
            return this;
        }

        public Builder registry(String registry) {
            this.registry = registry;
            return this;
        }

        public Builder assemblyDescriptor(String assemblyDescriptor) {
            this.assemblyDescriptor = assemblyDescriptor;
            return this;
        }

        public Builder assemblyDescriptorRef(String assemblyDescriptorRef) {
            this.assemblyDescriptorRef = assemblyDescriptorRef;
            return this;
        }

        public Builder ports(List<String> ports) {
            this.ports = ports;
            return this;
        }

        public Builder env(Map<String, String> env) {
            this.env = env;
            return this;
        }

        public Builder command(String command) {
            this.command = command;
            return this;
        }

        public BuildImageConfiguration build() {
            return new BuildImageConfiguration(from, exportDir, registry, assemblyDescriptor,
                                               assemblyDescriptorRef, ports, env, command);
        }
    }
}
