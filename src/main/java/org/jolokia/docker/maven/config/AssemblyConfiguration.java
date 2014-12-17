package org.jolokia.docker.maven.config;


public class AssemblyConfiguration {

    private static final String DEFAULT_ADD = "maven";
    private static final String DEFAULT_SRC = "src/main/docker";
    
    /**
     * @parameter
     */
    private String addFrom;
    
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
    private String dockerfile;
    
    /**
     * @parameter
     */
    private boolean dryRun;
    
    /**
     * @parameter
     */
    private boolean export;
    
    /**
     * @paramter
     */
    private boolean ignorePermissions;

    /**
     * @paramaeter
     */
    private String sourceDirectory;
    
    /**
     * @parameter
     */
    private String user;

    public AssemblyConfiguration() { }

    public String getAddFrom() {
        return (addFrom != null) ? addFrom: DEFAULT_ADD;
    }
    
    public String getBasedir() {
        return basedir;
    }

    public String getDescriptor() {
        return descriptor;
    }
    
    public String[] getDescriptors() {
        return (descriptor != null) ? new String[] { descriptor } : null;
    }
    
    public String getDescriptorRef() {
        return descriptorRef;
    }

    public String[] getDescriptorRefs() {
        return (descriptorRef != null) ? new String[] { descriptorRef } : null;
    }
   
    public String getDockerfile()
    {
        return dockerfile;
    }
    
    public String getSourceDirectory() {
        return (sourceDirectory != null) ? sourceDirectory : DEFAULT_SRC;
    }
    
    public String getUser() {
        return user;
    }
   
    public boolean isDryRun() {
        return dryRun;
    }
    
    public boolean exportBasedir() {
        return export;
    }
    
    public boolean isIgnorePermissions() {
        return ignorePermissions;
    }
    
    public static class Builder {
        
        private final AssemblyConfiguration config = new AssemblyConfiguration();
        
        public Builder addFrom(String addFrom) {
            config.addFrom = addFrom;
            return this;
        }
        
        public Builder basedir(String baseDir) {
            config.basedir = baseDir;
            return this;
        }

        public Builder descriptor(String descriptor) {
            config.descriptor = descriptor;
            return this;
        }
        
        public Builder descriptorRef(String descriptorRef) {
            config.descriptorRef = descriptorRef;
            return this;
        }

        public Builder dryRun(boolean dryRun) {
            config.dryRun = dryRun;
            return this;
        }

        public Builder exportBasedir(boolean export) {
            config.export = export;
            return this;
        }

        public Builder ignorePermissions(boolean ignorePermissions) {
            config.ignorePermissions = ignorePermissions;
            return this;
        }

        public Builder user(String user) {
            config.user = user;
            return this;
        }

        public AssemblyConfiguration build() {
            return config;
        }
    }

   
}
