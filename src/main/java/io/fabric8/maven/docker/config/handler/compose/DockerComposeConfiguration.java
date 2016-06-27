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
        // the @parameter tags above don't actually do anything, so we need to handle the default here :(
        return (basedir == null) ? "src/main/docker" : basedir;
    }

    public String getComposeFile() {
        return composeFile;
    }

    public static class Builder {

        private Map<String,String> config = new HashMap<>();

        public Builder composeFile(String composeFile) {
            config.put("composeFile", composeFile);
            return this;
        }

        public Builder basedir(String basedir) {
            config.put("basedir", basedir);
            return this;
        }

        public DockerComposeConfiguration build() {
            return new DockerComposeConfiguration(config);
        }
    }

}
