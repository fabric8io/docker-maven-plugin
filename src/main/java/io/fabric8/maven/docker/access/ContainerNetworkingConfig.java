package io.fabric8.maven.docker.access;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

public class ContainerNetworkingConfig {

    final JSONObject startConfig = new JSONObject();

    public ContainerNetworkingConfig() {}

    public ContainerNetworkingConfig endpointsConfig(Map<String, ContainerNetworkingEndpointsConfig> endpointsConfig) {
        final JSONObject endpointConfigMap = new JSONObject();
        for (Map.Entry<String, ContainerNetworkingEndpointsConfig> entry : endpointsConfig.entrySet()) {
            endpointConfigMap.put(entry.getKey(), entry.getValue().toJsonObject());
        }
        return add("EndpointsConfig", endpointConfigMap);
    }

    /**
     * Get JSON which is used for <em>starting</em> a container
     *
     * @return string representation for JSON representing the configuration for starting a container
     */
    public String toJson() {
        return startConfig.toString();
    }

    public Object toJsonObject() {
        return startConfig;
    }

    ContainerNetworkingConfig addAsArray(String propKey, List<String> props) {
        if (props != null) {
            startConfig.put(propKey, new JSONArray(props));
        }
        return this;
    }

    private ContainerNetworkingConfig add(String name, Object value) {
        if (value != null) {
            startConfig.put(name, value);
        }
        return this;
    }

}
