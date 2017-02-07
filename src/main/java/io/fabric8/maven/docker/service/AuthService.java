package io.fabric8.maven.docker.service;

import java.io.Serializable;
import java.util.Map;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.MojoParameters;

import org.apache.maven.plugin.MojoExecutionException;

/**
 *
 */
public class AuthService {

    public AuthConfig prepareAuthConfig(ImageName image, String configuredRegistry, boolean isPush, AuthParameters authParameters)
            throws MojoExecutionException {
        String user = isPush ? image.getUser() : null;
        String registry = image.getRegistry() != null ? image.getRegistry() : configuredRegistry;

        return authParameters.getAuthConfigFactory().createAuthConfig(isPush, authParameters.isSkipExtendedAuth(), authParameters.getAuthConfig(),
                authParameters.getMojoParameters().getSettings(), user, registry);
    }

    // ======================================

    public static class AuthParameters implements Serializable {

        private MojoParameters mojoParameters;

        private AuthConfigFactory authConfigFactory;

        private boolean skipExtendedAuth;

        private Map authConfig;

        public AuthParameters() {}

        public MojoParameters getMojoParameters() {
            return mojoParameters;
        }

        public void setMojoParameters(MojoParameters mojoParameters) {
            this.mojoParameters = mojoParameters;
        }

        public AuthConfigFactory getAuthConfigFactory() {
            return authConfigFactory;
        }

        public void setAuthConfigFactory(AuthConfigFactory authConfigFactory) {
            this.authConfigFactory = authConfigFactory;
        }

        public boolean isSkipExtendedAuth() {
            return skipExtendedAuth;
        }

        public void setSkipExtendedAuth(boolean skipExtendedAuth) {
            this.skipExtendedAuth = skipExtendedAuth;
        }

        public Map getAuthConfig() {
            return authConfig;
        }

        public void setAuthConfig(Map authConfig) {
            this.authConfig = authConfig;
        }

        // ===========================================

        public static class Builder {

            private AuthService.AuthParameters parameters = new AuthService.AuthParameters();

            public Builder() {
                this.parameters = new AuthService.AuthParameters();
            }

            public Builder mojoParameters(MojoParameters mojoParameters) {
                parameters.setMojoParameters(mojoParameters);
                return this;
            }

            public Builder authConfigFactory(AuthConfigFactory authConfigFactory) {
                parameters.setAuthConfigFactory(authConfigFactory);
                return this;
            }

            public Builder skipExtendedAuth(boolean skipExtendedAuth) {
                parameters.setSkipExtendedAuth(skipExtendedAuth);
                return this;
            }

            public Builder authConfig(Map authConfig) {
                parameters.setAuthConfig(authConfig);
                return this;
            }

            public AuthParameters build() {
                return parameters;
            }
        }
    }

}
