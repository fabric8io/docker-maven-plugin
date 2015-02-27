package org.jolokia.docker.maven.config;


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
    private String user;
    
    public AssemblyConfiguration() {
    }

    public Boolean exportBasedir() {
        return exportBasedir != null ? exportBasedir : Boolean.TRUE;
    }

    public String getBasedir() {
        return basedir != null ? basedir : DEFAULT_BASE_DIR;
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

        public Builder descriptor(String descriptor) {
            config.descriptor = set(descriptor);
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

         private <T> T set(T prop) {
            if (prop != null) {
                isEmpty = false;
            }
            return prop;
        }
    }
}
