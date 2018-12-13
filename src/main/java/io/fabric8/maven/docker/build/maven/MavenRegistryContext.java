package io.fabric8.maven.docker.build.maven;

import java.io.IOException;

import io.fabric8.maven.docker.build.auth.RegistryAuth;
import io.fabric8.maven.docker.build.RegistryContext;
import io.fabric8.maven.docker.build.auth.RegistryAuthConfig;
import io.fabric8.maven.docker.build.auth.RegistryAuthFactory;
import io.fabric8.maven.docker.config.build.ImagePullPolicy;

/**
 * @author roland
 * @since 17.10.18
 */
public class MavenRegistryContext implements RegistryContext {

    private ImagePullPolicy defaultImagePullPolicy;
    private RegistryAuthFactory registryAuthFactory;
    private String pushRegistry;
    private String pullRegistry;

    @Override
    public ImagePullPolicy getDefaultImagePullPolicy() {
        return defaultImagePullPolicy;
    }

    @Override
    public RegistryAuth getAuthConfig(RegistryAuthConfig.Kind kind, String user, String registry) throws IOException {
        return registryAuthFactory.createAuthConfig(kind, user, registry);
    }

    @Override
    public String getRegistry(RegistryAuthConfig.Kind kind) {
        return kind == RegistryAuthConfig.Kind.PULL ? pullRegistry : pushRegistry;
    }

    // ===============================================================================================

    public static class Builder {

        private MavenRegistryContext context = new MavenRegistryContext();

        public Builder defaultImagePullPolicy(ImagePullPolicy defaultImagePullPolicy) {
            context.defaultImagePullPolicy = defaultImagePullPolicy;
            return this;
        }

        public Builder pushRegistry(String pushRegistry) {
            context.pushRegistry = pushRegistry;
            return this;
        }

        public Builder pullRegistry(String pullRegistry) {
            context.pullRegistry = pullRegistry;
            return this;
        }

        public Builder authRegistryAuthFactory(RegistryAuthFactory registryAuthFactory) {
            context.registryAuthFactory = registryAuthFactory;
            return this;
        }

        // ================================================================================
        public MavenRegistryContext build() {
            return context;
        }

    }
}
