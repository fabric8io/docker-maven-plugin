package io.fabric8.maven.docker.access;

import java.io.UnsupportedEncodingException;
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

    private final String username;
    private final String password;
    private final String email;
    private final String auth;

    private final String authEncoded;

    public AuthConfig(Map<String,String> params) {
        this(params.get("username"),
             params.get("password"),
             params.get("email"),
             params.get("auth"));
    }

    public AuthConfig(String username, String password, String email, String auth) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.auth = auth;
        authEncoded = createAuthEncoded();
    }

    /**
     * Constructor which takes an base64 encoded credentials in the form 'user:password'
     *
     * @param credentialsEncoded the docker encoded user and password
     * @param email the email to use for authentication
     */
    public AuthConfig(String credentialsEncoded, String email) {
        String credentials = new String(Base64.decodeBase64(credentialsEncoded));
        String[] parsedCreds = credentials.split(":",2);
        username = parsedCreds[0];
        password = parsedCreds[1];
        this.email = email;
        auth = null;
        authEncoded = createAuthEncoded();
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String toHeaderValue() {
        return authEncoded;
    }

    // ======================================================================================================

    private String createAuthEncoded() {
        JSONObject ret = new JSONObject();
        putNonNull(ret, "username", username);
        putNonNull(ret, "password", password);
        putNonNull(ret, "email", email);
        putNonNull(ret, "auth", auth);
        try {
            return Base64.encodeBase64String(ret.toString().getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return Base64.encodeBase64String(ret.toString().getBytes());
        }
    }

    private void putNonNull(JSONObject ret, String key, String value) {
        if (value != null) {
            ret.put(key,value);
        }
    }
}
