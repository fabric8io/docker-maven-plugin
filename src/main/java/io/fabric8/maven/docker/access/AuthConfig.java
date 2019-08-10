package io.fabric8.maven.docker.access;

import com.google.gson.JsonObject;

import org.apache.commons.codec.binary.Base64;

import java.io.UnsupportedEncodingException;
import java.util.Map;

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
    private final String identityToken;

    private final String authEncoded;

    public AuthConfig(Map<String,String> params) {
        this(params.get("username"),
                params.get("password"),
                params.get("email"),
                params.get("auth"),
                params.get("identityToken"));
    }

    public AuthConfig(String username, String password, String email, String auth) {
        this(username, password, email, auth, null);
    }

    public AuthConfig(String username, String password, String email, String auth, String identityToken) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.auth = auth;
        this.identityToken = identityToken;
        authEncoded = createAuthEncoded();
    }

    /**
     * Constructor which takes an base64 encoded credentials in the form 'user:password'
     *
     * @param credentialsEncoded the docker encoded user and password
     * @param email the email to use for authentication
     */
    public AuthConfig(String credentialsEncoded, String email) {
        this(credentialsEncoded, email, null);
    }

    /**
     * Constructor which takes an base64 encoded credentials in the form 'user:password'
     *
     * @param credentialsEncoded the docker encoded user and password
     * @param email the email to use for authentication
     */
    public AuthConfig(String credentialsEncoded, String email, String identityToken) {
        String credentials = new String(Base64.decodeBase64(credentialsEncoded));
        String[] parsedCreds = credentials.split(":",2);
        username = parsedCreds[0];
        password = parsedCreds[1];
        this.email = email;
        this.identityToken = identityToken;
        auth = null;
        authEncoded = createAuthEncoded();
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getAuth() {
        return auth;
    }

    public String getIdentityToken() {
        return identityToken;
    }

    public String toHeaderValue() {
        return authEncoded;
    }

    // ======================================================================================================

    private String createAuthEncoded() {
        JsonObject ret = new JsonObject();
        if(identityToken != null) {
            putNonNull(ret, "identitytoken", identityToken);
        } else {
            putNonNull(ret, "username", username);
            putNonNull(ret, "password", password);
            putNonNull(ret, "email", email);
            putNonNull(ret, "auth", auth);
        }

        try {
            return encodeBase64ChunkedURLSafeString(ret.toString().getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return encodeBase64ChunkedURLSafeString(ret.toString().getBytes());
        }
    }

    /**
     * Encodes the given binaryData in a format that is compatible with the Docker Engine API.
     * That is, base64 encoded, padded, and URL safe.
     *
     * @param binaryData data to encode
     * @return encoded data
     */
    private String encodeBase64ChunkedURLSafeString(final byte[] binaryData) {
        return Base64.encodeBase64String(binaryData)
                .replace('+', '-')
                .replace('/', '_');
    }

    private void putNonNull(JsonObject ret, String key, String value) {
        if (value != null) {
            ret.addProperty(key,value);
        }
    }
}