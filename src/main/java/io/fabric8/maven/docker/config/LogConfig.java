package io.fabric8.maven.docker.config;

import java.util.Map;

/**
* @author rikcarve
* @since 10/02/16
*/
public class LogConfig {

    public static final LogConfig DEFAULT = new LogConfig();

    /**
     * @parameter
     */
    private String logDriver;

    /**
     * @parameter
     */
    private Map<String, String> logOpts;

    public LogConfig() {};

    public String getLogDriver() {
        return logDriver;
    }

    public Map<String, String> getLogOpts() {
        return logOpts;
    }

    // ================================================

    public static class Builder {

        private LogConfig policy = new LogConfig();

        public Builder logDriver(String logDriver) {
            policy.logDriver = logDriver;
            return this;
        }

        public Builder logOpts(Map<String, String> logOpts) {
            policy.logOpts = logOpts;
            return this;
        }

        public LogConfig build() {
            return policy;
        }
    }
}
