package io.fabric8.maven.docker.config;

import org.apache.maven.plugins.annotations.Parameter;

import java.io.Serializable;
import java.util.Map;

/**
 * @since 15/07/24
 */
public class SecretConfiguration implements Serializable {

    @Parameter
    private Map<String, String> envs;

    @Parameter
    private Map<String, String> files;

    public Map<String, String> getEnvs() {
        return envs;
    }

    public Map<String, String> getFiles() {
        return files;
    }

    public static class Builder {

        private final SecretConfiguration config = new SecretConfiguration();
        private boolean isEmpty = true;

        public SecretConfiguration build() {
            return isEmpty ? null : config;
        }

        public SecretConfiguration.Builder envs(Map<String, String> envs) {
            config.envs = envs;
            if (envs != null && !envs.isEmpty()) {
                isEmpty = false;
            }
            return this;
        }

        public SecretConfiguration.Builder files(Map<String, String> files) {
            config.files = files;
            if (files != null && !files.isEmpty()) {
                isEmpty = false;
            }
            return this;
        }
    }
}
