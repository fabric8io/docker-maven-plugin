package io.fabric8.maven.docker.config.maven;

import io.fabric8.maven.docker.config.build.BuildConfiguration;
import org.apache.commons.lang3.SerializationUtils;

/**
 * @author roland
 * @since 19.10.18
 */
public class MavenBuildConfiguration extends BuildConfiguration {

    private MavenAssemblyConfiguration assembly;

    @Override
    public MavenAssemblyConfiguration getAssemblyConfiguration() {
        return assembly;
    }

    public static class Builder extends BuildConfiguration.Builder {

        private MavenBuildConfiguration mavenConfig;

        public Builder() {
            this(null);
        }

        public Builder(MavenBuildConfiguration that) {
            if (that == null) {
                this.mavenConfig = new MavenBuildConfiguration();
                this.config =  mavenConfig;
            } else {
                this.config = SerializationUtils.clone(that);
            }
        }

        public Builder assembly(MavenAssemblyConfiguration assembly) {
            this.mavenConfig.assembly = assembly;
            return this;
        }

        @Override
        public MavenBuildConfiguration build() {
            return mavenConfig;
        }
    }

}
