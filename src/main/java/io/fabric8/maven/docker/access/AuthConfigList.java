package io.fabric8.maven.docker.access;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Container for multiple {@link AuthConfig} objects
 */
public class AuthConfigList {
    private final List<AuthConfig> authConfigs;

    public AuthConfigList() {
        authConfigs = new ArrayList<>();
    }

    public AuthConfigList(AuthConfig authConfig) {
        this();
        if (authConfig != null) {
            authConfigs.add(authConfig);
        }
    }

    public void addAuthConfig(AuthConfig authConfig) {
        Objects.requireNonNull(authConfig);
        authConfigs.add(authConfig);
    }

    public String toJson() {
        JsonObject auths = new JsonObject();
        for (AuthConfig authConfig: authConfigs) {
            JsonObject auth = authConfig.toJsonObject().getAsJsonObject("auths");
            for (Map.Entry<String, JsonElement> entry : auth.entrySet()) {
                auths.add(entry.getKey(), entry.getValue());
            }
        }
        JsonObject root = new JsonObject();
        if (auths.size() > 0) {
            root.add("auths", auths);
        }

        return root.toString();
    }

    public boolean isEmpty() {
        return authConfigs.isEmpty();
    }

    public int size() {
        return authConfigs.size();
    }
}
