package org.jolokia.docker.maven.config.external;

import java.util.*;

import org.jolokia.docker.maven.config.LogConfiguration;
import org.jolokia.docker.maven.config.WaitConfiguration;
import org.jolokia.docker.maven.config.WatchImageConfiguration;

public class DockerComposeConfiguration {

    /** @parameter */
    private String basedir;

    /** @parameter */
    private List<Service> services;

    /** @parameter */
    private String yamlFile;

    public String getBasedir() {
        // the @parameter tags above don't actually do anything, so we need to handle the default here :( 
        return (basedir == null) ? "src/main/docker" : basedir;
    }

    public Map<String, DockerComposeConfiguration.Service> getServiceMap() {
        if (services == null) {
            return Collections.emptyMap();
        }

        Map<String, DockerComposeConfiguration.Service> map = new HashMap<>(services.size());
        for (Service service : services) {
            map.put(service.id, service);
        }
        return map;
    }

    public String getYamlFile() {
        // see 'getBasedir'
        return (yamlFile == null) ? "docker-compose.yml" : yamlFile;
    }

    public static class Builder {
        private final DockerComposeConfiguration composeConfig = new DockerComposeConfiguration();
        private final List<Service> services = new ArrayList<>();
        
        public DockerComposeConfiguration build() {
            composeConfig.services = services;
            
            return composeConfig;
        }

        public Builder basedir(String basedir) {
            composeConfig.basedir = basedir;
            return this;
        }
        
        public Builder yamlFile(String yamlFile) {
            composeConfig.yamlFile = yamlFile;
            return this;
        }
    }

    public static class Service {
        /** @parameter */
        private String id;

        /** @parameter */
        private String image;

        /** @parameter */
        private LogConfiguration log;

        /** @parameter */
        private WaitConfiguration wait;

        /** @parameter */
        private WatchImageConfiguration watch;

        public String getImage() {
            return image;
        }

        public LogConfiguration getLogConfiguration() {
            return log;
        }

        public WaitConfiguration getWaitConfiguration() {
            return wait;
        }

        public WatchImageConfiguration getWatchImageConfiguration() {
            return watch;
        }
    }

}
