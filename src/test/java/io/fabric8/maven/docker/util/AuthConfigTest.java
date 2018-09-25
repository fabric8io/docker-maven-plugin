package io.fabric8.maven.docker.util;

import com.google.gson.JsonObject;

import org.apache.commons.codec.binary.Base64;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.maven.docker.access.AuthConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author roland
 * @since 30.07.14
 */
public class AuthConfigTest {


    @Test
    public void simpleConstructor() {
        Map<String,String> map = new HashMap<String,String>();
        map.put("username","roland");
        map.put("password","#>secrets??");
        map.put("email","roland@jolokia.org");
        AuthConfig config = new AuthConfig(map);
        check(config);
    }

    @Test
    public void mapConstructor() {
        AuthConfig config = new AuthConfig("roland","#>secrets??","roland@jolokia.org",null);
        check(config);
    }

    @Test
    public void dockerLoginConstructor() {
        AuthConfig config = new AuthConfig(Base64.encodeBase64String("roland:#>secrets??".getBytes()),"roland@jolokia.org");
        check(config);
    }

    private void check(AuthConfig config) {
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