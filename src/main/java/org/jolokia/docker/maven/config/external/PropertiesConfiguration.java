package org.jolokia.docker.maven.config.external;

public class PropertiesConfiguration {

    /** @parameter */
    private String prefix;

    public String getPrefix() {
        return prefix;
    }

    public static class Builder {
        private final PropertiesConfiguration propsConfig = new PropertiesConfiguration();

        public Builder prefix(String prefix) {
            propsConfig.prefix = prefix;
            return this;
        }

        public PropertiesConfiguration build() {
            return propsConfig;
        }
    }
}
