package com.amazonaws.auth;

/**
 * Shameless copy of the original for testing {@link io.fabric8.maven.docker.util.aws.AwsSdkAuthConfigFactory}.
 * Based on <tt>com.amazonaws:aws-java-sdk-core:1.11.707</tt>.
 */
public class AWSSessionCredentials extends AWSCredentials {

    private final String sessionKey;

    public AWSSessionCredentials(String accessKeyId, String secretKey, String sessionKey) {
        super(accessKeyId, secretKey);
        this.sessionKey = sessionKey;
    }

    public String getSessionToken() {return sessionKey;}

}
