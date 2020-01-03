package io.fabric8.maven.docker.util.aws;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class AwsSdkAuthConfigFactoryTest {

    @Mocked
    private AWSCredentialsProvider credentialsProvider;

    @Mocked
    private Logger log;

    private AwsSdkAuthConfigFactory objectUnderTest;


    @Before
    public void setup() {
        objectUnderTest = new AwsSdkAuthConfigFactory(credentialsProvider, log);
    }

    @Test
    public void exceptionsAreHandledGracefully() {
        new Expectations() {{
            credentialsProvider.getCredentials();
            minTimes = 1;
            result = new SdkClientException("Unauthorized");
        }};

        AuthConfig authConfig = objectUnderTest.createAuthConfig();

        assertNull(authConfig);
    }

    @Test
    public void nullValueIsPassedOn() {
        new Expectations() {{
            credentialsProvider.getCredentials();
            minTimes = 1;
            result = null;
        }};

        AuthConfig authConfig = objectUnderTest.createAuthConfig();

        assertNull(authConfig);
    }

    @Test
    public void basicCredentialsAreTransformedIntoAuthConfig() {
        String accessKey = "accessKey";
        String secretKey = "secretKey";
        new Expectations() {{
            credentialsProvider.getCredentials();
            minTimes = 1;
            this.result = new BasicAWSCredentials(accessKey, secretKey);
        }};

        AuthConfig authConfig = objectUnderTest.createAuthConfig();

        assertNotNull(authConfig);
        assertEquals(accessKey, authConfig.getUsername());
        assertEquals(secretKey, authConfig.getPassword());
        assertNull(authConfig.getAuth());
        assertNull(authConfig.getIdentityToken());
    }

    @Test
    public void sessionCredentialsAreTransformedIntoAuthConfig() {
        String accessKey = "accessKey";
        String secretKey = "secretKey";
        String sessionToken = "sessionToken";
        new Expectations() {{
            credentialsProvider.getCredentials();
            minTimes = 1;
            this.result = new BasicSessionCredentials(accessKey, secretKey, sessionToken);
        }};

        AuthConfig authConfig = objectUnderTest.createAuthConfig();

        assertNotNull(authConfig);
        assertEquals(accessKey, authConfig.getUsername());
        assertEquals(secretKey, authConfig.getPassword());
        assertEquals(sessionToken, authConfig.getAuth());
        assertNull(authConfig.getIdentityToken());
    }

}