package io.fabric8.maven.docker.config.maven;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;


/**
 * Class holding configuration for accessing a registry
 */
public class RegistryAuthConfiguration implements Serializable {

    private Map<String, String> push;

    private Map<String, String> pull;

    private String username;

    private String password;

    private String email;

    private String authToken;

    public Map<String, String> getPush() {
        return push;
    }

    public Map<String, String> getPull() {
        return pull;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public String getAuthToken() {
        return authToken;
    }

}
