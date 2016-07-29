package io.fabric8.maven.docker.access;

import java.util.List;

import io.fabric8.maven.docker.config.NetworkConfig;
import org.json.JSONArray;
import org.json.JSONObject;

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
        endPoints.put("Aliases",new JSONArray(config.getAliases()));

        JSONObject endpointConfigMap = new JSONObject();
        endpointConfigMap.put(config.getCustomNetwork(), endPoints);

        networkingConfig.put("EndpointsConfig", endpointConfigMap);
        return this;
    }

    public String toJson() {
        return networkingConfig.toString();
    }

    public Object toJsonObject() {
        return networkingConfig;
    }
}
