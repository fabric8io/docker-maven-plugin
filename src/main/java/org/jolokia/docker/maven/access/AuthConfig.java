package org.jolokia.docker.maven.access;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject;

/**
 * Configuration object holding auth information for
 * pushing to Docker
 *
 * @author roland
 * @since 22.07.14
 */
public class AuthConfig {

    public final static AuthConfig EMPTY_AUTH_CONFIG = new AuthConfig("", "", "", "");

    private Map<String,String> params;

    public AuthConfig(Map<String,String> params) {
        this.params = params;
    }

    public AuthConfig(String user, String password, String email,String auth) {
        params = new HashMap<String,String>();
        putNonNull(params, "username", user);
        putNonNull(params, "password", password);
        putNonNull(params, "email", email);
        putNonNull(params, "auth", auth);
    }

    public String toHeaderValue() {
        JSONObject ret = new JSONObject();
        add(ret,"username");
        add(ret,"password");
        add(ret,"email");
        add(ret,"auth");
        try {
            return Base64.encodeBase64String(ret.toString().getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return Base64.encodeBase64String(ret.toString().getBytes());
        }
    }


    // ======================================================================================================

    private void add(JSONObject ret, String key) {
        if (params.containsKey(key)) {
            ret.put(key,params.get(key));
        }
    }

    private void putNonNull(Map ret, String key, String value) {
        if (value != null) {
            ret.put(key,value);
        }
    }
}
