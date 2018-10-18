package io.fabric8.maven.docker.config;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

import io.fabric8.maven.docker.build.maven.AuthConfigFactory;
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
    private String authToken;

    public Map toMap() {
        final Map authMap = new TreeMap<>();

        if (push != null) {
            authMap.put("push", push);
        }
        if (pull != null) {
            authMap.put("pull", pull);
        }
        if (StringUtils.isNotBlank(username)) {
            authMap.put(AuthConfigFactory.AUTH_USERNAME, username);
        }
        if (StringUtils.isNotBlank(password)) {
            authMap.put(AuthConfigFactory.AUTH_PASSWORD, password);
        }
        if (StringUtils.isNotBlank(authToken)) {
            authMap.put(AuthConfigFactory.AUTH_AUTHTOKEN, authToken);
        }
        if (StringUtils.isNotBlank(email)) {
            authMap.put(AuthConfigFactory.AUTH_EMAIL, email);
        }
        return authMap;
    }
}
