package io.fabric8.maven.docker.access;

import org.json.JSONArray;
import org.json.JSONObject;

import io.fabric8.maven.docker.config.NetworkConfig;

import static io.fabric8.maven.docker.util.JsonUtils.put;

public class ContainerNetworkingConfig {

    private final JSONObject networkingConfig = new JSONObject();

    /**
     * Add networking aliases to a custom network
     *
     * @param config network config as configured in the pom.xml
     * @return this configuration
     */
    public ContainerNetworkingConfig aliases(NetworkConfig config) {
        JSONObject endPoints = new JSONObject();
        put(endPoints,"Aliases",new JSONArray(config.getAliases()));

        JSONObject endpointConfigMap = new JSONObject();
        put(endpointConfigMap, config.getCustomNetwork(), endPoints);

        put(networkingConfig, "EndpointsConfig", endpointConfigMap);
        return this;
    }

    public String toJson() {
        return networkingConfig.toString();
    }

    public Object toJsonObject() {
        return networkingConfig;
    }
}
