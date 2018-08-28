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
        map.put("password","secret");
        map.put("email","roland@jolokia.org");
        AuthConfig config = new AuthConfig(map);
        check(config);
    }

    @Test
    public void mapConstructor() {
        AuthConfig config = new AuthConfig("roland","secret","roland@jolokia.org",null);
        check(config);
    }

    @Test
    public void dockerLoginConstructor() {
        AuthConfig config = new AuthConfig(Base64.encodeBase64String("roland:secret".getBytes()),"roland@jolokia.org");
        check(config);
    }

    private void check(AuthConfig config) {
        String header = new String(Base64.decodeBase64(config.toHeaderValue()));
        JsonObject data = GsonBridge.toJsonObject(header);
        assertEquals("roland",data.get("username").getAsString());
        assertEquals("secret",data.get("password").getAsString());
        assertEquals("roland@jolokia.org",data.get("email").getAsString());
        assertFalse(data.has("auth"));
    }
}
