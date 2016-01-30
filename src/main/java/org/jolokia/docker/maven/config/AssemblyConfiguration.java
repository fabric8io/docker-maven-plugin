package org.jolokia.docker.maven.config;


import org.apache.maven.plugin.assembly.model.Assembly;

public class AssemblyConfiguration {

    private static final String DEFAULT_BASE_DIR = "/maven";
    
    /**
     * @parameter
     */
    private String basedir;

    /**
     * @parameter
     */
    private String descriptor;

    /**
     * @parameter
     */
    private Assembly inline;

    /**
     * @parameter
     */
    private String descriptorRef;

    /**
     * @parameter
     */
    private String dockerFileDir;

    /**
     * @parameter default-value="true"
     */
    private Boolean exportBasedir;

    /**
     * @paramter default-value="false"
     */
    private Boolean ignorePermissions;

    /**
     * @parameter
     */
    private AssemblyMode mode;

    /**
     * @parameter
     */
    private String user;

    public Boolean exportBasedir() {
        return exportBasedir;
    }

    public String getBasedir() {
        return basedir != null ? basedir : DEFAULT_BASE_DIR;
    }

    public Assembly getInline() {
        return inline;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public String getDescriptorRef() {
        return descriptorRef;
    }

    public String getDockerFileDir() {
        return dockerFileDir;
    }

    public String getUser() {
        return user;
    }

    public AssemblyMode getMode() {
        return mode != null ? mode : AssemblyMode.dir;
    }

    public Boolean isIgnorePermissions() {
        return (ignorePermissions != null) ? ignorePermissions : Boolean.FALSE;
    }
    
    public static class Builder {

        private final AssemblyConfiguration config = new AssemblyConfiguration();
        private boolean isEmpty = true;

        public AssemblyConfiguration build() {
            return isEmpty ? null : config;
        }

        public Builder basedir(String baseDir) {
            config.basedir = set(baseDir);
            return this;
        }

        public Builder assemblyDef(Assembly descriptor) {
            config.inline = set(descriptor);
            return this;
        }

        public Builder descriptor(String descriptorFile) {
            config.descriptor = set(descriptorFile);
            return this;
        }

        public Builder descriptorRef(String descriptorRef) {
            config.descriptorRef = set(descriptorRef);
            return this;
        }

        public Builder dockerFileDir(String dockerFileDir) {
            config.dockerFileDir = set(dockerFileDir);
            return this;
        }
        
        public Builder exportBasedir(Boolean export) {
            config.exportBasedir = set(export);
            return this;
        }

        public Builder ignorePermissions(Boolean ignorePermissions) {
            config.ignorePermissions = set(ignorePermissions);
            return this;
        }

        public Builder user(String user) {
            config.user = set(user);
            return this;
        }

        public Builder mode(String mode) {
            if (mode != null) {
                config.mode = AssemblyMode.valueOf(mode.toLowerCase());
                isEmpty = false;
            }
            return this;
        }

         private <T> T set(T prop) {
            if (prop != null) {
                isEmpty = false;
            }
            return prop;
        }
    }
}
