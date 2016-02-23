package io.fabric8.maven.docker.util;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.maven.docker.access.AuthConfig;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

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
        JSONObject data = new JSONObject(header);
        assertEquals("roland",data.getString("username"));
        assertEquals("secret",data.getString("password"));
        assertEquals("roland@jolokia.org",data.getString("email"));
        assertFalse(data.has("auth"));
    }
}
