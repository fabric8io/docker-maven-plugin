package io.fabric8.maven.docker.access;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class ContainerNetworkingEndpointsConfig {

    final JSONObject startConfig = new JSONObject();

    public ContainerNetworkingEndpointsConfig() {}

    public ContainerNetworkingEndpointsConfig aliases(List<String> aliases) {
        return addAsArray("Aliases", aliases);
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

    ContainerNetworkingEndpointsConfig addAsArray(String propKey, List<String> props) {
        if (props != null) {
            startConfig.put(propKey, new JSONArray(props));
        }
        return this;
    }

    private ContainerNetworkingEndpointsConfig add(String name, Object value) {
        if (value != null) {
            startConfig.put(name, value);
        }
        return this;
    }

}
