package io.fabric8.maven.docker.access;

import com.google.gson.JsonObject;

import io.fabric8.maven.docker.config.NetworkConfig;
import io.fabric8.maven.docker.util.GsonBridge;

public class ContainerNetworkingConfig {

    private final JsonObject networkingConfig = new JsonObject();

    /**
     * Add networking aliases to a custom network
     *
     * @param config network config as configured in the pom.xml
     * @return this configuration
     */
    public ContainerNetworkingConfig aliases(NetworkConfig config) {
        JsonObject endPoints = new JsonObject();
        endPoints.add("Aliases", GsonBridge.toJsonArray(config.getAliases()));

        JsonObject endpointConfigMap = new JsonObject();
        endpointConfigMap.add(config.getCustomNetwork(), endPoints);

        networkingConfig.add("EndpointsConfig", endpointConfigMap);
        return this;
    }

    public String toJson() {
        return networkingConfig.toString();
    }

    public JsonObject toJsonObject() {
        return networkingConfig;
    }
}
