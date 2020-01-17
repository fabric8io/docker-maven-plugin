package io.fabric8.maven.docker.util.aws;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class AwsSdkAuthConfigFactoryTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Mocked
    private Logger log;
    private AwsSdkAuthConfigFactory objectUnderTest;


    @Before
    public void setup() {
        objectUnderTest = new AwsSdkAuthConfigFactory(log);
    }

    @Test
    public void nullValueIsPassedOn() {
        AuthConfig authConfig = objectUnderTest.createAuthConfig();

        assertNull(authConfig);
    }

    @Test
    public void reflectionWorksForBasicCredentials() {
        String accessKey = randomUUID().toString();
        String secretKey = randomUUID().toString();
        environmentVariables.set("AWSCredentials.AWSAccessKeyId", accessKey);
        environmentVariables.set("AWSCredentials.AWSSecretKey", secretKey);

        AuthConfig authConfig = objectUnderTest.createAuthConfig();

        assertNotNull(authConfig);
        assertEquals(accessKey, authConfig.getUsername());
        assertEquals(secretKey, authConfig.getPassword());
        assertNull(authConfig.getAuth());
        assertNull(authConfig.getIdentityToken());
    }

    @Test
    public void reflectionWorksForSessionCredentials() {
        String accessKey = randomUUID().toString();
        String secretKey = randomUUID().toString();
        String sessionToken = randomUUID().toString();
        environmentVariables.set("AWSCredentials.AWSAccessKeyId", accessKey);
        environmentVariables.set("AWSCredentials.AWSSecretKey", secretKey);
        environmentVariables.set("AWSSessionCredentials.SessionToken", sessionToken);

        AuthConfig authConfig = objectUnderTest.createAuthConfig();

        assertNotNull(authConfig);
        assertEquals(accessKey, authConfig.getUsername());
        assertEquals(secretKey, authConfig.getPassword());
        assertEquals(sessionToken, authConfig.getAuth());
        assertNull(authConfig.getIdentityToken());
    }

    /*
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
*/
}