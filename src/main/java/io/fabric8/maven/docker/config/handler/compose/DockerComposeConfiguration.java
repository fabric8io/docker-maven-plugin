package io.fabric8.maven.docker.config.handler.compose;

import java.util.*;

public class DockerComposeConfiguration {

    private final String basedir;
    private final String composeFile;
    private final boolean ignoreBuild;
    public DockerComposeConfiguration(Map<String, String> config) {
        basedir = config.containsKey("basedir") ? config.get("basedir") : "src/main/docker";
        composeFile = config.containsKey("composeFile") ? config.get("composeFile") : "docker-compose.yml";
        ignoreBuild = config.containsKey("ignoreBuild") ? Boolean.parseBoolean(config.get("ignoreBuilder")) : false;
    }

    String getBasedir() {
        return basedir;
    }

    String getComposeFile() {
        return composeFile;
    }

    public boolean isIgnoreBuild() {
        return ignoreBuild;
    }
}
