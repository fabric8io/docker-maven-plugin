package com.amazonaws.auth;

import io.fabric8.maven.docker.access.AuthConfig;

import static java.lang.System.getenv;

/** Shameless copy of the original for testing {@link io.fabric8.maven.docker.util.aws.AwsSdkAuthConfigFactory} */
public final class DefaultAWSCredentialsProviderChain {

    public AWSCredentials getCredentials() {
        String accessKeyId = getenv("AWSCredentials.AWSAccessKeyId");
        if (accessKeyId == null) {
            return null;
        }
        String secretKey = getenv("AWSCredentials.AWSSecretKey");
        String sessionToken = getenv("AWSSessionCredentials.SessionToken");
        return sessionToken == null
                ? new AWSCredentials(accessKeyId, secretKey)
                : new AWSSessionCredentials(accessKeyId,secretKey,sessionToken);
    }

}