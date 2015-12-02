package org.jolokia.docker.maven.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;
import org.jolokia.docker.maven.access.AuthConfig;
import org.json.JSONObject;
import org.junit.Test;

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
        map.put("serveraddress","private.registry.com");
        AuthConfig config = new AuthConfig(map);
        check(config);
    }

    @Test
    public void mapConstructor() {
        AuthConfig config = new AuthConfig("roland","secret","roland@jolokia.org",null,
                                           "private.registry.com");
        check(config);
    }

    private void check(AuthConfig config) {
        String header = new String(Base64.decodeBase64(config.toHeaderValue()));
        JSONObject data = new JSONObject(header);
        assertEquals("roland",data.getString("username"));
        assertEquals("secret",data.getString("password"));
        assertEquals("roland@jolokia.org",data.getString("email"));
        assertEquals("private.registry.com",data.getString("serveraddress"));
        assertFalse(data.has("auth"));
    }
}
