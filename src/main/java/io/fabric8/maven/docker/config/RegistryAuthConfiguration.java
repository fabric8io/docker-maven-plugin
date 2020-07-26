package io.fabric8.maven.docker.config;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

import io.fabric8.maven.docker.access.AuthConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugins.annotations.Parameter;

public class RegistryAuthConfiguration implements Serializable {

    @Parameter
    private Map<String, String> push;

    @Parameter
    private Map<String, String> pull;

    @Parameter
    private String username;

    @Parameter
    private String password;

    @Parameter
    private String email;

    @Parameter
    @Deprecated
    private String authToken;

    @Parameter
    private String auth;

    public Map toMap() {
        final Map authMap = new TreeMap<>();

        if (push != null) {
            authMap.put("push", push);
        }
        if (pull != null) {
            authMap.put("pull", pull);
        }
        if (StringUtils.isNotBlank(username)) {
            authMap.put(AuthConfig.AUTH_USERNAME, username);
        }
        if (StringUtils.isNotBlank(password)) {
            authMap.put(AuthConfig.AUTH_PASSWORD, password);
        }

        if (StringUtils.isNotBlank(authToken) && StringUtils.isNotBlank(auth)) {
            throw new IllegalStateException("For a registry configuration either 'auth' or 'authToken' (deprecated) can be specified but not both. Use only 'auth' and remove 'authToken' in the registry configuration");
        }

        if (StringUtils.isNotBlank(authToken)) {
            authMap.put(AuthConfig.AUTH_AUTH, authToken);
        }

        if (StringUtils.isNotBlank(auth)) {
            authMap.put(AuthConfig.AUTH_AUTH, auth);
        }

        if (StringUtils.isNotBlank(email)) {
            authMap.put(AuthConfig.AUTH_EMAIL, email);
        }
        return authMap;
    }
}
