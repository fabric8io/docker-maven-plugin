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
                "eyJwYXNzd29yZCI6IiM-c2VjcmV0cz8_IiwiZW1haWwiOiJyb2xhbmRAam9sb2tpYS5vcmciLCJ1c2VybmFtZSI6InJvbGFuZCJ9",
                config.toHeaderValue()
        );

        String header = new String(Base64.decodeBase64(config.toHeaderValue()));

        JsonObject data = GsonBridge.toJsonObject(header);
        assertEquals("roland",data.getString("username"));
        assertEquals("#>secrets??",data.getString("password"));
        assertEquals("roland@jolokia.org",data.getString("email"));
        assertFalse(data.has("auth"));
    }
}
