package io.fabric8.maven.docker.config.handler.compose;

import java.util.*;

public class DockerComposeConfiguration {

    private String basedir;
    private String composeFile;

    public DockerComposeConfiguration(Map<String, String> config) {
        basedir = config.containsKey("basedir") ? config.get("basedir") : "src/main/docker";
        composeFile = config.containsKey("composeFile") ? config.get("composeFile") : "docker-compose.yml";
    }

    public String getBasedir() {
        return basedir;
    }

    public String getComposeFile() {
        return composeFile;
    }
}
