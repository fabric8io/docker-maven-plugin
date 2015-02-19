package org.jolokia.docker.maven.config;


public class AssemblyConfiguration {

    public static final AssemblyConfiguration EMPTY = new AssemblyConfiguration(true);
    
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
    
    private Boolean empty;
    
    public AssemblyConfiguration() {  
        this(false);
    }

    private AssemblyConfiguration(boolean empty) {
        this.empty = empty;
    }
    
    public Boolean exportBasedir() {
        if (empty) {
            return false;
        }
        
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

    public Boolean isEmpty() {
        return empty;
    }
    
    public Boolean isIgnorePermissions() {
        return (ignorePermissions != null) ? ignorePermissions : Boolean.FALSE;
    }
    
    public static class Builder {

        private final AssemblyConfiguration config = new AssemblyConfiguration(true);

        public Builder basedir(String baseDir) {
            config.basedir = baseDir;
            return this;
        }

        public AssemblyConfiguration build() {
            return config;
        }

        public Builder descriptor(String descriptor) {
            config.descriptor = descriptor;
            setEmpty(descriptor);
            
            return this;
        }

        public Builder descriptorRef(String descriptorRef) {
            config.descriptorRef = descriptorRef;
            setEmpty(descriptorRef);
            
            return this;
        }

        public Builder dockerFileDir(String dockerFileDir) {
            config.dockerFileDir = dockerFileDir;
            return this;
        }
        
        public Builder exportBasedir(Boolean export) {
            config.exportBasedir = export;
            return this;
        }

        public Builder ignorePermissions(Boolean ignorePermissions) {
            config.ignorePermissions = ignorePermissions;
            return this;
        }

        public Builder user(String user) {
            config.user = user;
            return this;
        }
        
        private void setEmpty(String descriptorOrRef) {
            if (descriptorOrRef != null) {
                config.empty = false;
            }
        }
    }
}
