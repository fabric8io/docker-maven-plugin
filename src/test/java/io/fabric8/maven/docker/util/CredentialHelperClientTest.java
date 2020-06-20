package io.fabric8.maven.docker.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.fabric8.maven.docker.access.AuthConfig;
import mockit.Mocked;
import mockit.Tested;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CredentialHelperClientTest {
    private final Gson gson = new Gson();

    @Mocked
    private Logger logger;

    private CredentialHelperClient credentialHelperClient;

    private JsonObject jsonObject;

    private AuthConfig authConfig;

    @Before
    public void givenCredentialHelperClient() {
        this.credentialHelperClient = new CredentialHelperClient(logger, "desktop");
    }

    @Test
    public void testUsernamePasswordAuthConfig() {
        givenJson("{\"ServerURL\":\"registry.mycompany.com\",\"Username\":\"jane_doe\",\"Secret\":\"not-really\"}");

        whenJsonObjectConvertedToAuthConfig();
        
        assertEquals("username should match", "jane_doe", this.authConfig.getUsername());
        assertEquals("password should match", "not-really", this.authConfig.getPassword());
        assertNull("identityToken should not be set", this.authConfig.getIdentityToken());
    }

    @Test
    public void testTokenAuthConfig() {
        givenJson("{\"ServerURL\":\"registry.cloud-provider.com\",\"Username\":\"<token>\",\"Secret\":\"gigantic-mess-of-jwt\"}");

        whenJsonObjectConvertedToAuthConfig();

        assertNull("username should not be set", this.authConfig.getUsername());
        assertNull("password should not be set", this.authConfig.getPassword());
        assertEquals("identity token should match", "gigantic-mess-of-jwt", this.authConfig.getIdentityToken());
    }

    private void givenJson(String json) {
        this.jsonObject = this.gson.fromJson(json, JsonObject.class);
    }

    private void whenJsonObjectConvertedToAuthConfig() {
        this.authConfig = this.credentialHelperClient.toAuthConfig(this.jsonObject);
    }
}
