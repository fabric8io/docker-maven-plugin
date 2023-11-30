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
     * Attestation configuration
     */
    @Parameter
    private AttestationConfiguration attestations;

    @Parameter
    private String nodeName;

    /**
     * Value to be passed to {@code --cache-from} option of BuildX build.
     */
    @Parameter
    private String cacheFrom;

    /**
     * Value to be passed to {@code --cache-to} option of BuildX build.
     */
    @Parameter
    private String cacheTo;

    public String getBuilderName() {
        return builderName;
    }

    public String getConfigFile() {
        return configFile;
    }

    public String getDockerStateDir() {
        return dockerStateDir;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getCacheFrom() {
        return cacheFrom;
    }

    public String getCacheTo() {
        return cacheTo;
    }

    public boolean isBuildX() {
        return !getPlatforms().isEmpty();
    }

    @Nonnull
    public List<String> getPlatforms() {
        return EnvUtil.splitAtCommasAndTrim(platforms);
    }

    public AttestationConfiguration getAttestations() {
        return attestations;
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

        public Builder nodeName(String nodeName) {
            config.nodeName = nodeName;
            if (nodeName != null) {
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

        public Builder attestations(AttestationConfiguration attestations) {
            config.attestations = attestations;
            if (attestations != null) {
                isEmpty = false;
            }
            return this;
        }

        public Builder cacheFrom(String cacheFrom) {
            config.cacheFrom = cacheFrom;
            if (cacheFrom != null) {
                isEmpty = false;
            }
            return this;
        }

        public Builder cacheTo(String cacheTo) {
            config.cacheTo = cacheTo;
            if (cacheTo != null) {
                isEmpty = false;
            }
            return this;
        }
    }
}
