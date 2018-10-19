package io.fabric8.maven.docker.config.maven;

import io.fabric8.maven.docker.config.build.AssemblyConfiguration;
import org.apache.maven.plugins.assembly.model.Assembly;

/**
 * @author roland
 * @since 19.10.18
 */
public class MavenAssemblyConfiguration extends AssemblyConfiguration {

    /**
     * Assembly defined inline in the pom.xml
     */
    private Assembly inline;

    public Assembly getInline() {
        return inline;
    }

    public static class Builder extends AssemblyConfiguration.Builder {

        private final MavenAssemblyConfiguration mavenConfig;

        public Builder() {
            mavenConfig = new MavenAssemblyConfiguration();
            config = mavenConfig;
        }

        public Builder assemblyDef(Assembly descriptor) {
            mavenConfig.inline = set(descriptor);
            return this;
        }

        @Override
        public MavenAssemblyConfiguration build() {
            return mavenConfig;
        }
    }
}
