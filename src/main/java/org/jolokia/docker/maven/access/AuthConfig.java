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

    private String authEncoded;

    public AuthConfig(Map<String,String> params) {
        this.authEncoded = createAuthEncoded(params);
    }

    public AuthConfig(String user, String password, String email, String auth) {
        Map<String,String>  params = new HashMap<>();
        putNonNull(params, "username", user);
        putNonNull(params, "password", password);
        putNonNull(params, "email", email);
        putNonNull(params, "auth", auth);

        this.authEncoded = createAuthEncoded(params);
    }

    public AuthConfig(String authEncoded) {
        this.authEncoded = authEncoded;
    }

    public String toHeaderValue() {
        return authEncoded;
    }

    // ======================================================================================================

    private String createAuthEncoded(Map<String,String> params) {
        JSONObject ret = new JSONObject();
        add(params,ret,"username");
        add(params,ret,"password");
        add(params,ret,"email");
        add(params,ret,"auth");
        try {
            return Base64.encodeBase64String(ret.toString().getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return Base64.encodeBase64String(ret.toString().getBytes());
        }
    }

    private void add(Map<String,String> params, JSONObject ret, String key) {
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
