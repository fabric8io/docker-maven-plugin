package io.fabric8.maven.docker.access;

import com.google.gson.JsonObject;

public class NetworkCreateConfig {
    final JsonObject createConfig = new JsonObject();
    final String name;

    public NetworkCreateConfig(String name) {
        this.name = name;
        createConfig.addProperty("Name", name);
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
