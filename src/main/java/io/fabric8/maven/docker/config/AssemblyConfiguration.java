package io.fabric8.maven.docker.config;


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
     * @deprecated Use {@link BuildImageConfiguration#dockerFileDir} instead
     */
    private String dockerFileDir;

    /**
     * @parameter default-value="true"
     */
    private Boolean exportBasedir;

    /**
     * @paramter default-value="false"
     * @deprecated use permissionMode == ignore instead.
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

    /**
     * @parameter default-value="keep"
     */
    private PermissionMode permissions;

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
        // New permission mode has precedence
        if (permissions != null) {
            return permissions == PermissionMode.ignore;
        }
        return (ignorePermissions != null) ? ignorePermissions : Boolean.FALSE;
    }

    public PermissionMode getPermissions() {
        return permissions != null ? permissions : PermissionMode.keep;
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

        @Deprecated
        public Builder ignorePermissions(Boolean ignorePermissions) {
            config.ignorePermissions = set(ignorePermissions);
            return this;
        }

        public Builder permissions(String permissions) {
            if (permissions != null) {
                config.permissions = PermissionMode.valueOf(permissions.toLowerCase());
            }
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

    public enum PermissionMode {

        /**
         * Auto detect permission mode
         */
        auto,

        /**
         * Make everything executable
         */
        exec,

        /**
         * Leave all as it is
         */
        keep,

        /**
         * Ignore permission when using an assembly mode of "dir"
         */
        ignore
        }
}
