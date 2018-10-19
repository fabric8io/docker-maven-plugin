package io.fabric8.maven.docker.config.maven;

import io.fabric8.maven.docker.config.ImageConfiguration;
import org.apache.commons.lang3.SerializationUtils;

/**
 * @author roland
 * @since 19.10.18
 */
public class MavenImageConfiguration extends ImageConfiguration {

    private MavenBuildConfiguration build;

    @Override
    public MavenBuildConfiguration getBuildConfiguration() {
        return build;
    }

    public static class Builder extends  ImageConfiguration.Builder {

        private MavenImageConfiguration mavenConfig;

        public Builder() {
            this(null);
        }

        public Builder buildConfig(MavenBuildConfiguration build) {
            mavenConfig.build = build;
            return this;
        }

        public Builder(MavenImageConfiguration that) {
            if (that == null) {
                this.mavenConfig = new MavenImageConfiguration();
                this.config = mavenConfig;
            } else {
                this.config = SerializationUtils.clone(that);
            }
        }

        @Override
        public MavenImageConfiguration build() {
            return mavenConfig;
        }
    }
}
