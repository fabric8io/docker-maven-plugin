package io.fabric8.maven.docker.build.auth;

import java.io.UnsupportedEncodingException;
import java.util.Optional;
import java.util.function.Function;

import com.google.gson.JsonObject;
import java.util.Base64;

/**
 * Configuration object holding auth information for
 * pushing to Docker
 *
 * @author roland
 * @since 22.07.14
 */
public class RegistryAuth {

    public final static RegistryAuth EMPTY_REGISTRY_AUTH =
        new RegistryAuth.Builder().username("").password("").email("").auth("").build();

    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String EMAIL = "email";
    public static final String AUTH = "authToken";

    private String username, password, email, auth, authEncoded;

    private RegistryAuth() { }

    public static RegistryAuth fromRegistryAuthConfig(RegistryAuthConfig registryAuthConfig,
                                                      RegistryAuthConfig.Kind kind,
                                                      Function<String, String> decryptor) {
        return new Builder()
            .username(registryAuthConfig.getUsername(kind))
            .email(registryAuthConfig.getEmail(kind))
            .auth(registryAuthConfig.getAuth(kind))
            .password(registryAuthConfig.getPassword(kind), decryptor)
            .build();
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

    public String toHeaderValue() {
        return authEncoded;
    }

    // ======================================================================================================

    public static class Builder {
        RegistryAuth registryAuth;

        public Builder() {
            registryAuth = new RegistryAuth();
        }

        public Builder username(String username) {
            registryAuth.username = username;
            return this;
        }

        public Builder password(String password) {
            return password(password, null);
        }

        public Builder password(String password, Function<String, String> decryptor) {
            registryAuth.password =
                Optional.ofNullable(decryptor).map(d -> d.apply(password)).orElse(password);
            return this;
        }

        public Builder email(String email) {
            registryAuth.email = email;
            return this;
        }

        public Builder auth(String auth) {
            registryAuth.auth = auth;
            return this;
        }

        public Builder withCredentialsEncoded(String creds) {
            String credentials = new String(Base64.getDecoder().decode(creds));
            String[] parsedCreds = credentials.split(":",2);
            registryAuth.username = parsedCreds[0];
            registryAuth.password = parsedCreds[1];
            return this;
        }


        public RegistryAuth build() {
            registryAuth.authEncoded = registryAuth.createAuthEncoded();
            return registryAuth;
        }
    }


    // ======================================================================================================

    private String createAuthEncoded() {
        JsonObject ret = new JsonObject();
        putNonNull(ret, USERNAME, username);
        putNonNull(ret, PASSWORD, password);
        putNonNull(ret, EMAIL, email);
        putNonNull(ret, AUTH, auth);
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
        return Base64.getEncoder().encodeToString(binaryData)
                     .replace('+', '-')
                     .replace('/', '_');
    }

    private void putNonNull(JsonObject ret, String key, String value) {
        if (value != null) {
            ret.addProperty(key,value);
        }
    }
}