package io.fabric8.maven.docker.config.handler.compose;

import java.util.*;

public class DockerComposeConfiguration {

    private final String basedir;
    private final String composeFile;
    private final boolean ignoreBuild;
    
    public DockerComposeConfiguration(Map<String, String> config) {
        
    basedir = config.getOrDefault("basedir", "src/main/docker");
    composeFile = config.getOrDefault("composeFile", "docker-compose.yml");
    ignoreBuild = Boolean.parseBoolean(config.getOrDefault("ignoreBuild", "false"));
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
