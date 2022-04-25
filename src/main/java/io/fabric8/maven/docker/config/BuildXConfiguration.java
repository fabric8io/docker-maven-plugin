package io.fabric8.maven.docker.config;

import io.fabric8.maven.docker.util.EnvUtil;
import org.apache.maven.plugins.annotations.Parameter;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.List;

public class BuildXConfiguration implements Serializable {

    /**
     * List of platforms for multi-architecture build
     */
    @Parameter
    private List<String> platforms;

    /**
     * Location of docker cache
     */
    @Parameter
    private String cache;

    @Nonnull
    public List<String> getPlatforms() {
        return EnvUtil.removeEmptyEntries(platforms);
    }

    public String getCache() {
        return cache;
    }

    public boolean isBuildX() {
        return !getPlatforms().isEmpty();
    }

    public static class Builder {

        private final BuildXConfiguration config = new BuildXConfiguration();
        private boolean isEmpty = true;

        public BuildXConfiguration build() {
            return isEmpty ? null : config;
        }

        public Builder platforms(List<String> platforms) {
            config.platforms = EnvUtil.removeEmptyEntries(platforms);
            if (!config.platforms.isEmpty()) {
                isEmpty = false;
            }
            return this;
        }

        public Builder cache(String cache) {
            config.cache = cache;
            if (cache != null) {
                isEmpty = false;
            }
            return this;
        }
    }
}
