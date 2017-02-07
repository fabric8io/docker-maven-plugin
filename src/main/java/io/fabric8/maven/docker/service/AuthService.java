package io.fabric8.maven.docker.service;

import java.io.Serializable;
import java.util.Map;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.MojoParameters;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * Service to obtain authentication information for pushing to Docker registry.
 */
public class AuthService {

    public AuthConfig prepareAuthConfig(ImageName image, String configuredRegistry, boolean isPush, AuthContext authContext)
            throws MojoExecutionException {
        String user = isPush ? image.getUser() : null;
        String registry = image.getRegistry() != null ? image.getRegistry() : configuredRegistry;

        return authContext.getAuthConfigFactory().createAuthConfig(isPush, authContext.isSkipExtendedAuth(), authContext.getAuthConfig(),
                authContext.getMojoParameters().getSettings(), user, registry);
    }

    // ======================================================

    public static class AuthContext implements Serializable {

        private MojoParameters mojoParameters;

        private AuthConfigFactory authConfigFactory;

        private boolean skipExtendedAuth;

        private Map authConfig;

        public AuthContext() {
        }

        public MojoParameters getMojoParameters() {
            return mojoParameters;
        }

        public AuthConfigFactory getAuthConfigFactory() {
            return authConfigFactory;
        }

        public boolean isSkipExtendedAuth() {
            return skipExtendedAuth;
        }

        public Map getAuthConfig() {
            return authConfig;
        }

        public static class Builder {

            private AuthContext context = new AuthContext();

            public Builder() {
                this.context = new AuthContext();
            }

            public Builder(AuthContext context) {
                this.context = context;
            }

            public Builder mojoParameters(MojoParameters mojoParameters) {
                context.mojoParameters = mojoParameters;
                return this;
            }

            public Builder authConfigFactory(AuthConfigFactory authConfigFactory) {
                context.authConfigFactory = authConfigFactory;
                return this;
            }

            public Builder skipExtendedAuth(boolean skipExtendedAuth) {
                context.skipExtendedAuth = skipExtendedAuth;
                return this;
            }

            public Builder authConfig(Map authConfig) {
                context.authConfig = authConfig;
                return this;
            }

            public AuthContext build() {
                return context;
            }
        }
    }

}
