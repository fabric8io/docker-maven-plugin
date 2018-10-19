package io.fabric8.maven.docker.config.build;


import java.io.Serializable;

public class AssemblyConfiguration implements Serializable {

    /**
     * New replacement for base directory which better reflects its
     * purpose
     */
    private String targetDir;

    /**
     * Name of the assembly which is used also as name of the archive
     * which is created and has to be used when providing an own Dockerfile
     */
    private String name = "maven";

    private String descriptor;

    private String descriptorRef;

    /**
     * Whether the target directory should be
     * exported.
     */
    private Boolean exportTargetDir;

    private PermissionMode permissions;

    private AssemblyMode mode;

    private String user;

    private String tarLongFileMode;

    public Boolean getExportTargetDir() {
        return exportTargetDir;
    }

    public String getTargetDir() {
        if (targetDir != null) {
            return targetDir;
        } else {
            return "/" + getName();
        }
    }

    public String getDescriptor() {
        return descriptor;
    }

    public String getDescriptorRef() {
        return descriptorRef;
    }

    public String getUser() {
        return user;
    }

    public AssemblyMode getMode() {
        return mode;
    }

    public String getTarLongFileMode() {
        return tarLongFileMode;
    }

     public PermissionMode getPermissions() {
        return permissions;
    }

    public String getName() {
        return name;
    }

    public static class Builder {

        protected AssemblyConfiguration config;

        public Builder() {
            config = new AssemblyConfiguration();
        }

        private boolean isEmpty = true;

        public AssemblyConfiguration build() {
            return isEmpty ? null : config;
        }

        public Builder exportTargetDir(Boolean exportTargetDir) {
            config.exportTargetDir = exportTargetDir;
            return this;
        }

        public Builder targetDir(String targetDir) {
            config.targetDir = set(targetDir);
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

        public Builder permissions(String permissions) {
            if (permissions != null) {
                config.permissions = PermissionMode.valueOf(permissions.toLowerCase());
                isEmpty = false;
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

        public Builder tarLongFileMode(String tarLongFileMode) {
            config.tarLongFileMode = set(tarLongFileMode);
            return this;
        }

        protected <T> T set(T prop) {
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
