package io.fabric8.maven.docker.config;

import io.fabric8.maven.docker.util.EnvUtil;
import org.apache.maven.plugins.annotations.Parameter;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.List;

public class BuildXConfiguration implements Serializable {

    /**
     * Builder instance name
     */
    @Parameter
    private String builderName;
    /**
     * Configuration file to create builder
     */
    @Parameter
    private String configFile;

    /**
     * Location of docker state, including builder configurations
     */
    @Parameter
    private String dockerStateDir;

    /**
     * List of platforms for multi-architecture build
     */
    @Parameter
    private List<String> platforms;

    /**
     * Use buildx only on push phase.
     * This will speed up the build overall as buildx will not
     * be used to load the image locally
     */
    @Parameter(defaultValue = "false")
    private boolean useBuildxOnlyOnPush;

    @Nonnull
    public List<String> getPlatforms() {
        return EnvUtil.splitAtCommasAndTrim(platforms);
    }

    public String getBuilderName() {
        return builderName;
    }

    public String getConfigFile() {
        return configFile;
    }

    public String getDockerStateDir() {
        return dockerStateDir;
    }

    public boolean isBuildX() {
        return !getPlatforms().isEmpty();
    }

    public boolean useBuildxOnlyOnPush() {
        return useBuildxOnlyOnPush;
    }

    public static class Builder {

        private final BuildXConfiguration config = new BuildXConfiguration();
        private boolean isEmpty = true;

        public BuildXConfiguration build() {
            return isEmpty ? null : config;
        }

        public Builder builderName(String builderName) {
            config.builderName = builderName;
            if (builderName != null) {
                isEmpty = false;
            }
            return this;
        }

        public Builder configFile(String configFile) {
            config.configFile = configFile;
            if (configFile != null) {
                isEmpty = false;
            }
            return this;
        }

        public Builder dockerStateDir(String dockerStateDir) {
            config.dockerStateDir = dockerStateDir;
            if (dockerStateDir != null) {
                isEmpty = false;
            }
            return this;
        }

        public Builder platforms(List<String> platforms) {
            config.platforms = EnvUtil.removeEmptyEntries(platforms);
            if (!config.platforms.isEmpty()) {
                isEmpty = false;
            }
            return this;
        }

        public Builder useBuildxOnlyOnPush(boolean useBuildxOnlyOnPush) {
            config.useBuildxOnlyOnPush = useBuildxOnlyOnPush;
            return this;
        }
    }
}
