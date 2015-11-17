package org.jolokia.docker.maven.config.external;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jolokia.docker.maven.config.LogConfiguration;
import org.jolokia.docker.maven.config.WaitConfiguration;
import org.jolokia.docker.maven.config.WatchImageConfiguration;

public class DockerComposeConfiguration {

    private static final String DEFAULT_FILE = "docker-compose.yml";

    /**
     * @parameter
     * @required
     */
    private String dockerComposeDir;

    /** @parameter */
    private List<Service> extended;

    public String getComposeFilePath() {
        return Paths.get(dockerComposeDir, DEFAULT_FILE).toString();
    }

    public String getDockerComposeDir() {
        return dockerComposeDir;
    }

    public Map<String, DockerComposeConfiguration.Service> getServiceMap() {
        if (extended == null) {
            return Collections.emptyMap();
        }

        Map<String, DockerComposeConfiguration.Service> map = new HashMap<>(extended.size());
        for (Service service : extended) {
            map.put(service.id, service);
        }
        return map;
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

    public static class Builder {
        private final DockerComposeConfiguration composeConfig = new DockerComposeConfiguration();

        public Builder dockerComposeDir(String dockerComposeDir) {
            composeConfig.dockerComposeDir = dockerComposeDir;
            return this;
        }

        public DockerComposeConfiguration build() {
            return composeConfig;
        }
    }

}
