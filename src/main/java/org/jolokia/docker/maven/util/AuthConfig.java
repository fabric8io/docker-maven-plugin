package org.jolokia.docker.maven.util;

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

    private Map<String,String> params;

    public AuthConfig(Map<String,String> params) {
        this.params = params;
    }

    public AuthConfig(String user, String password, String email) {
        params = new HashMap<String,String>();
        putNonNull(params, "user", user);
        putNonNull(params, "password", password);
        putNonNull(params, "email", email);
    }

    public String toHeaderValue() {
        JSONObject ret = new JSONObject();
        add(ret,"user");
        add(ret,"password");
        add(ret,"email");
        return Base64.encodeBase64String(ret.toString().getBytes());
    }

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
