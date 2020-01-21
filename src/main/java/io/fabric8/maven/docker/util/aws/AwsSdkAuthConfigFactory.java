package io.fabric8.maven.docker.util.aws;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.util.Logger;

public class AwsSdkAuthConfigFactory {

    private final Logger log;

    public AwsSdkAuthConfigFactory(Logger log) {
        this.log = log;
    }

    public AuthConfig createAuthConfig() {
        try {
            Class<?> credentialsProviderChainClass = Class.forName("com.amazonaws.auth.DefaultAWSCredentialsProviderChain");
            Object credentialsProviderChain = credentialsProviderChainClass.getDeclaredConstructor().newInstance();
            Object credentials = credentialsProviderChainClass.getMethod("getCredentials").invoke(credentialsProviderChain);
            if (credentials == null) {
                return null;
            }

            Class<?> sessionCredentialsClass = Class.forName("com.amazonaws.auth.AWSSessionCredentials");
            String sessionToken = sessionCredentialsClass.isInstance(credentials)
                    ? (String) sessionCredentialsClass.getMethod("getSessionToken").invoke(credentials) : null;

            Class<?> credentialsClass = Class.forName("com.amazonaws.auth.AWSCredentials");
            return new AuthConfig(
                    (String) credentialsClass.getMethod("getAWSAccessKeyId").invoke(credentials),
                    (String) credentialsClass.getMethod("getAWSSecretKey").invoke(credentials),
                    "none",
                    sessionToken
            );
        } catch (Throwable t) {
            log.warn("AWS SDK detected, but failed to fetch AWS credentials: %s", t);
            return null;
        }
    }

}