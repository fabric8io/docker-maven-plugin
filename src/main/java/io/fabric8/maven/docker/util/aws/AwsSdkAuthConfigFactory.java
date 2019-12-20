package io.fabric8.maven.docker.util.aws;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.util.Logger;

public class AwsSdkAuthConfigFactory {
    private final Logger log;

    public AwsSdkAuthConfigFactory(Logger log) {
        this.log = log;
    }

    public AuthConfig createAuthConfig() {
        AWSCredentials credentials;
        try {
            credentials = new DefaultAWSCredentialsProviderChain().getCredentials();
        } catch (SdkClientException e) {
            log.debug("Failed to fetch AWS credentials: %s", e);
            return null;
        }
        if (credentials == null) {
            return null;
        }
        String sessionToken = (credentials instanceof AWSSessionCredentials)
                ? ((AWSSessionCredentials) credentials).getSessionToken() : null;
        return new AuthConfig(credentials.getAWSAccessKeyId(), credentials.getAWSSecretKey(), "none", sessionToken);
    }

}