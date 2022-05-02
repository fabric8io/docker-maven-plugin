package io.fabric8.maven.docker.util;

import com.google.gson.JsonObject;
import io.fabric8.maven.docker.access.AuthConfig;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;


/**
 * @author roland
 * @since 30.07.14
 */
class AuthConfigTest {

    @Test
    void simpleConstructor() {
        Map<String,String> map = new HashMap<String,String>();
        map.put(AuthConfig.AUTH_USERNAME,"roland");
        map.put(AuthConfig.AUTH_PASSWORD,"#>secrets??");
        map.put(AuthConfig.AUTH_EMAIL,"roland@jolokia.org");
        AuthConfig config = new AuthConfig(map);
        check(config);
    }

    @Test
    void mapConstructor() {
        AuthConfig config = new AuthConfig("roland","#>secrets??","roland@jolokia.org",null);
        check(config);
    }

    @Test
    void dockerLoginConstructor() {
        AuthConfig config = new AuthConfig(Base64.encodeBase64String("roland:#>secrets??".getBytes()),"roland@jolokia.org");
        check(config);
    }

    @Test
    void toJsonConfig() {
        AuthConfig config = new AuthConfig("king.roland", "12345", "king_roland@druidia.com", null);
        config.setRegistry("druidia.com/registry");
        Assertions.assertEquals("{\"auths\":{\"druidia.com/registry\":{\"auth\":\"a2luZy5yb2xhbmQ6MTIzNDU=\"}}}", config.toJson());
    }

    private void check(AuthConfig config) {
        // Since Base64.decodeBase64 handles URL-safe encoding, must explicitly check
        // the correct characters are used
        Assertions.assertEquals(
                "eyJ1c2VybmFtZSI6InJvbGFuZCIsInBhc3N3b3JkIjoiIz5zZWNyZXRzPz8iLCJlbWFpbCI6InJvbGFuZEBqb2xva2lhLm9yZyJ9",
                config.toHeaderValue()
        );

        String header = new String(Base64.decodeBase64(config.toHeaderValue()));

        JsonObject data = JsonFactory.newJsonObject(header);
        Assertions.assertEquals("roland",data.get(AuthConfig.AUTH_USERNAME).getAsString());
        Assertions.assertEquals("#>secrets??",data.get(AuthConfig.AUTH_PASSWORD).getAsString());
        Assertions.assertEquals("roland@jolokia.org",data.get(AuthConfig.AUTH_EMAIL).getAsString());
        Assertions.assertFalse(data.has(AuthConfig.AUTH_AUTH));
    }
}