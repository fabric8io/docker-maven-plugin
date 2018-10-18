package io.fabric8.maven.docker.build.maven;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.build.RegistryContext;
import io.fabric8.maven.docker.config.ImagePullPolicy;

/**
 * @author roland
 * @since 17.10.18
 */
public class MavenRegistryContext implements RegistryContext {

    private ImagePullPolicy defaultImagePullPolicy;
    private AuthConfigFactory authConfigFactory;
    private String pushRegistry;
    private String pullRegistry;

    @Override
    public ImagePullPolicy getDefaultImagePullPolicy() {
        return defaultImagePullPolicy;
    }

        @Override
    public AuthConfig lookupRegistryAuthConfig(boolean isPush, String user, String registry) {
        return authConfigFactory.createAuthConfig(isPush, user, registry);
    }

    @Override
    public String getPushRegistry() {
        return pushRegistry;
    }

    @Override
    public String getPullRegistry() {
        return pullRegistry;
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

        public Builder authConfigFactory(AuthConfigFactory authConfigFactory) {
            context.authConfigFactory = authConfigFactory;
            return this;
        }

        // ================================================================================
        public MavenRegistryContext build() {
            return context;
        }

    }
}
