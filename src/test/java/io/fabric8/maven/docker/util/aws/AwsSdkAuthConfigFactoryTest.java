package io.fabric8.maven.docker.util.aws;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.util.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static java.util.UUID.randomUUID;

@ExtendWith(MockitoExtension.class)
@ExtendWith(SystemStubsExtension.class)
class AwsSdkAuthConfigFactoryTest {

    @Mock
    private Logger log;

    @SystemStub
    private EnvironmentVariables environmentVariables;

    private AwsSdkAuthConfigFactory objectUnderTest;

    @BeforeEach
    void setup() {
        objectUnderTest = new AwsSdkAuthConfigFactory(log);
    }

    @Test
    void nullValueIsPassedOn() {
        AuthConfig authConfig = objectUnderTest.createAuthConfig();

        Assertions.assertNull(authConfig);
    }

    @Test
    void reflectionWorksForBasicCredentials() {
        String accessKey = randomUUID().toString();
        String secretKey = randomUUID().toString();
        environmentVariables
            .set("AWSCredentials.AWSAccessKeyId", accessKey)
            .set("AWSCredentials.AWSSecretKey", secretKey);

        AuthConfig authConfig = objectUnderTest.createAuthConfig();

        Assertions.assertNotNull(authConfig);
        Assertions.assertEquals(accessKey, authConfig.getUsername());
        Assertions.assertEquals(secretKey, authConfig.getPassword());
        Assertions.assertNull(authConfig.getAuth());
        Assertions.assertNull(authConfig.getIdentityToken());
    }

    @Test
    void reflectionWorksForSessionCredentials() {
        String accessKey = randomUUID().toString();
        String secretKey = randomUUID().toString();
        String sessionToken = randomUUID().toString();
        environmentVariables
            .set("AWSCredentials.AWSAccessKeyId", accessKey)
            .set("AWSCredentials.AWSSecretKey", secretKey)
            .set("AWSSessionCredentials.SessionToken", sessionToken);

        AuthConfig authConfig = objectUnderTest.createAuthConfig();

        Assertions.assertNotNull(authConfig);
        Assertions.assertEquals(accessKey, authConfig.getUsername());
        Assertions.assertEquals(secretKey, authConfig.getPassword());
        Assertions.assertEquals(sessionToken, authConfig.getAuth());
        Assertions.assertNull(authConfig.getIdentityToken());
    }

}