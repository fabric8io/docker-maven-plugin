package io.fabric8.maven.docker.config;

import java.io.Serializable;

/**
 * Build configuration for health checks.
 */
public class HealthCheckConfiguration implements Serializable {

    private HealthCheckMode mode = HealthCheckMode.cmd;

    private String interval;

    private String timeout;

    private String startPeriod;

    private Integer retries;

    private Arguments cmd;

    public HealthCheckConfiguration() {}

    public String getInterval() {
        return prepareTimeValue(interval);
    }

    public String getTimeout() {
        return prepareTimeValue(timeout);
    }

    public String getStartPeriod() {
        return prepareTimeValue(startPeriod);
    }

    private String prepareTimeValue(String timeout) {
        // Seconds as default
        if (timeout == null) {
            return null;
        }
        return timeout.matches("^\\d+$") ? timeout + "s" : timeout;
    }

    public Arguments getCmd() {
        return cmd;
    }

    public HealthCheckMode getMode() {
        return mode;
    }

    public Integer getRetries() {
        return retries;
    }

    public void validate() throws IllegalArgumentException {
        if (mode == null) {
            throw new IllegalArgumentException("HealthCheck: mode must not be null");
        }

        switch(mode) {
        case none:
            if (interval != null || timeout != null || startPeriod != null || retries != null || cmd != null) {
                throw new IllegalArgumentException("HealthCheck: no parameters are allowed when the health check mode is set to 'none'");
            }
            break;
        case cmd:
            if (cmd == null) {
                throw new IllegalArgumentException("HealthCheck: the parameter 'cmd' is mandatory when the health check mode is set to 'cmd' (default)");
            }
        }
    }

    // ===========================================

    public static class Builder {

        private HealthCheckConfiguration config = new HealthCheckConfiguration();

        public Builder() {
            this.config = new HealthCheckConfiguration();
        }

        public Builder interval(String interval) {
            config.interval = interval;
            return this;
        }

        public Builder timeout(String timeout) {
            config.timeout = timeout;
            return this;
        }

        public Builder startPeriod(String startPeriod) {
            config.startPeriod = startPeriod;
            return this;
        }

        public Builder cmd(Arguments command) {
            if (command != null) {
                config.cmd = command;
            }
            return this;
        }

        public Builder retries(Integer retries) {
            config.retries = retries;
            return this;
        }

        public Builder mode(String mode) {
            return this.mode(mode != null ? HealthCheckMode.valueOf(mode) : (HealthCheckMode) null);
        }

        public Builder mode(HealthCheckMode mode) {
            config.mode = mode;
            return this;
        }

        public HealthCheckConfiguration build() {
            return config;
        }
    }
}
