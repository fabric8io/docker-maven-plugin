package io.fabric8.maven.docker.build.auth;

import com.google.gson.JsonObject;

import io.fabric8.maven.docker.util.JsonFactory;
import org.apache.commons.codec.binary.Base64;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author roland
 * @since 30.07.14
 */
public class RegistryAuthTest {


    @Test
    public void simpleConstructor() {
        RegistryAuth config = new RegistryAuth.Builder()
            .username("roland")
            .password("#>secrets??")
            .email("roland@jolokia.org")
            .build();
        check(config);
    }

    @Test
    public void dockerLoginConstructor() {
        RegistryAuth config =
            new RegistryAuth.Builder()
                .withCredentialsEncoded(Base64.encodeBase64String("roland:#>secrets??".getBytes()))
                .email("roland@jolokia.org")
                .build();
        check(config);
    }

    private void check(RegistryAuth config) {
        // Since Base64.decodeBase64 handles URL-safe encoding, must explicitly check
        // the correct characters are used
        assertEquals(
                "eyJ1c2VybmFtZSI6InJvbGFuZCIsInBhc3N3b3JkIjoiIz5zZWNyZXRzPz8iLCJlbWFpbCI6InJvbGFuZEBqb2xva2lhLm9yZyJ9",
                config.toHeaderValue()
        );

        String header = new String(Base64.decodeBase64(config.toHeaderValue()));

        JsonObject data = JsonFactory.newJsonObject(header);
        assertEquals("roland",data.get("username").getAsString());
        assertEquals("#>secrets??",data.get("password").getAsString());
        assertEquals("roland@jolokia.org",data.get("email").getAsString());
        assertFalse(data.has("auth"));
    }
}