package io.fabric8.maven.docker.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.fabric8.maven.docker.access.AuthConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CredentialHelperClientTest {
    private final Gson gson = new Gson();

    @Mock
    private Logger logger;

    private CredentialHelperClient credentialHelperClient;

    private JsonObject jsonObject;

    private AuthConfig authConfig;

    @BeforeEach
    void givenCredentialHelperClient() {
        this.credentialHelperClient = new CredentialHelperClient(logger, "desktop");
    }

    @Test
    void testUsernamePasswordAuthConfig() {
        givenJson("{\"ServerURL\":\"registry.mycompany.com\",\"Username\":\"jane_doe\",\"Secret\":\"not-really\"}");

        whenJsonObjectConvertedToAuthConfig();

        Assertions.assertEquals("jane_doe", this.authConfig.getUsername(), "username should match");
        Assertions.assertEquals("not-really", this.authConfig.getPassword(), "password should match");
        Assertions.assertNull(this.authConfig.getIdentityToken(), "identityToken should not be set");
    }

    @Test
    void testTokenAuthConfig() {
        givenJson("{\"ServerURL\":\"registry.cloud-provider.com\",\"Username\":\"<token>\",\"Secret\":\"gigantic-mess-of-jwt\"}");

        whenJsonObjectConvertedToAuthConfig();

        Assertions.assertNull(this.authConfig.getUsername(), "username should not be set");
        Assertions.assertNull(this.authConfig.getPassword(), "password should not be set");
        Assertions.assertEquals("gigantic-mess-of-jwt", this.authConfig.getIdentityToken(), "identity token should match");
    }

    private void givenJson(String json) {
        this.jsonObject = this.gson.fromJson(json, JsonObject.class);
    }

    private void whenJsonObjectConvertedToAuthConfig() {
        this.authConfig = this.credentialHelperClient.toAuthConfig(this.jsonObject);
    }
}
