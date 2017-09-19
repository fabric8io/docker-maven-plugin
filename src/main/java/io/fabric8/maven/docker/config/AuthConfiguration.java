package io.fabric8.maven.docker.config;

import java.io.Serializable;
import java.util.Map;

import io.fabric8.maven.docker.util.DeepCopy;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author heapifyman
 * @since 19.09.17
 */
public class AuthConfiguration implements Serializable {

    @Parameter
    private Map<String, String> push;

    @Parameter
    private Map<String, String> pull;

    @Parameter
    private String username;

    @Parameter
    private String password;

    public AuthConfiguration() {
    }

    public Map<String, String> getPush() {
        return push;
    }

    public Map<String, String> getPull() {
        return pull;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    // ======================================================================================

    public static class Builder {

        public Builder(AuthConfiguration config) {
            if (config == null) {
                this.config = new AuthConfiguration();
            } else {
                this.config = DeepCopy.copy(config);
            }
        }

        public Builder() {
            this(null);
        }

        private AuthConfiguration config;

        public Builder push(Map<String, String> push) {
            config.push = push;
            return this;
        }

        public Builder pull(Map<String, String> pull) {
            config.pull = pull;
            return this;
        }

        public Builder username(String username) {
            config.username = username;
            return this;
        }

        public Builder password(String password) {
            config.password = password;
            return this;
        }

        public AuthConfiguration build() {
            return config;
        }
    }
}
