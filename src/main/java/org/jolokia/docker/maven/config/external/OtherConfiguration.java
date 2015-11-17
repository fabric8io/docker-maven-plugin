package org.jolokia.docker.maven.config.external;

import java.util.Map;

public class OtherConfiguration {

    /** @parameter */
    private Map<String, String> configuration;

    /**
     * @parameter
     * @required
     */
    private String type;

    public Map<String, String> getConfiguration() {
        return configuration;
    }

    public String getType() {
        return type;
    }

    public static class Builder {
        private OtherConfiguration otherConfig;

        public OtherConfiguration build() {
            return otherConfig;
        }

        public Builder configuration(String name, String value) {
            otherConfig.configuration.put(name, value);
            return this;
        }

        public Builder type(String type) {
            otherConfig.type = type;
            return this;
        }
    }
}
