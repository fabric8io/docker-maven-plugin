package org.jolokia.docker.maven.config.external;

import java.util.*;

import org.jolokia.docker.maven.config.*;

public class DockerComposeConfiguration {

    /** @parameter */
    private String basedir;

    /** @parameter */
    private List<ImageConfiguration> services = new ArrayList<>();

    /** @parameter */
    private String yamlFile;

    public String getBasedir() {
        // the @parameter tags above don't actually do anything, so we need to handle the default here :(
        return (basedir == null) ? "src/main/docker" : basedir;
    }

    public Map<String, ImageConfiguration> getServiceMap() {
        if (services == null) {
            return Collections.emptyMap();
        }

        Map<String, ImageConfiguration> map = new HashMap<>(services.size());
        for (ImageConfiguration service : services) {
            String alias = service.getAlias();
            if (alias == null) {
                throw new IllegalArgumentException("an 'alias' is required when using docker-compose files");
            }

            map.put(alias, service);
        }

        return map;
    }

    public String getYamlFile() {
        // see 'getBasedir'
        return (yamlFile == null) ? "docker-compose.yml" : yamlFile;
    }

    public static class Builder {
        private final DockerComposeConfiguration composeConfig = new DockerComposeConfiguration();

        public Builder addService(ImageConfiguration service) {
            composeConfig.services.add(service);
            return this;
        }

        public Builder basedir(String basedir) {
            composeConfig.basedir = basedir;
            return this;
        }

        public DockerComposeConfiguration build() {
            return composeConfig;
        }

        public Builder yamlFile(String yamlFile) {
            composeConfig.yamlFile = yamlFile;
            return this;
        }
    }

}
