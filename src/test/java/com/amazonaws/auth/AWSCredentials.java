package com.amazonaws.auth;

/**
 * Shameless copy of the original for testing {@link io.fabric8.maven.docker.util.aws.AwsSdkAuthConfigFactory}.
 * Based on <tt>com.amazonaws:aws-java-sdk-core:1.11.707</tt>.
 */
public class AWSCredentials {
    private final String accessKeyId;
    private final String secretKey;

    public AWSCredentials(String accessKeyId, String secretKey) {
        this.accessKeyId = accessKeyId;
        this.secretKey = secretKey;
    }

    public String getAWSAccessKeyId() {
        return accessKeyId;
    }

    public String getAWSSecretKey() {
        return secretKey;
    }

}
