package org.jolokia.docker.maven.config;

import java.util.*;

import org.apache.maven.plugin.MojoExecutionException;

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
    private String maintainer;

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
    private Arguments entryPoint;

    /**
     * @parameter
     * @deprecated
     */
    private String command;

    /**
     * @parameter
     */
    private String workdir;

    /**
     * @parameter
     */
    private Arguments cmd;

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

    public String getMaintainer() {
        return maintainer;
    }

    public String getWorkdir() {
        return workdir;
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
    
    public Arguments getCmd() {
        return cmd;
    }

    @Deprecated
    public String getCommand() {
        return command;
    }

    public Arguments getEntryPoint() {
        return entryPoint;
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

        public Builder maintainer(String maintainer) {
            config.maintainer = maintainer;
            return this;
        }

        public Builder workdir(String workdir) {
            config.workdir = workdir;
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

        public Builder cmd(String cmd) {
            if (config.cmd == null) {
                config.cmd = new Arguments();
            }
            config.cmd.setShell(cmd);
            return this;
        }

        public Builder entryPoint(String entryPoint) {
            if (config.entryPoint == null) {
                config.entryPoint = new Arguments();
            }
            config.entryPoint.setShell(entryPoint);
            return this;
        }

        public BuildImageConfiguration build() {
            return config;
        }
    }

    public void validate() throws MojoExecutionException {
        if (entryPoint != null) {
            entryPoint.validate();
        }
        if (cmd != null) {
            cmd.validate();
        }
    }
}
