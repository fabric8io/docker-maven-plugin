package org.jolokia.docker.maven.config.external;

public class ExternalImageConfiguration {

    /** @parameter */
    private DockerComposeConfiguration compose;

    /** @parameter */
    private PropertiesConfiguration properties;

    /** @parameter */
    private OtherConfiguration other;

    public DockerComposeConfiguration getComposeConfiguration() {
        return compose;
    }

    public boolean hasDockerCompose() {
        return compose != null;
    }

    public boolean hasProperties() {
        return properties != null;
    }

    public boolean hasOther() {
        return other != null;
    }

    public PropertiesConfiguration getPropertiesConfiguration() {
        return properties;
    }

    public OtherConfiguration getOther() {
        return other;
    }

    public static class Builder {
        private final ExternalImageConfiguration externalConfig = new ExternalImageConfiguration();

        public Builder compose(DockerComposeConfiguration compose) {
            externalConfig.compose = compose;
            return this;
        }

        public Builder properties(PropertiesConfiguration properties) {
            externalConfig.properties = properties;
            return this;
        }

        public Builder other(OtherConfiguration other) {
            externalConfig.other = other;
            return this;
        }

        public ExternalImageConfiguration build() {
            return externalConfig;
        }
    }
}
