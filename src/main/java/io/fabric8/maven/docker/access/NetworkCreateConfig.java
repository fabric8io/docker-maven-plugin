package io.fabric8.maven.docker.access;

import org.json.JSONObject;

import static io.fabric8.maven.docker.util.JsonUtils.put;

public class NetworkCreateConfig {
    final JSONObject createConfig = new JSONObject();
    final String name;

    public NetworkCreateConfig(String name) {
        this.name = name;
        put(createConfig, "Name", name);
    }

    public String getName() {
        return name;
    }

    /**
     * Get JSON which is used for <em>creating</em> a network
     *
     * @return string representation for JSON representing creating a network
     */
    public String toJson() {
        return createConfig.toString();
    }
}
