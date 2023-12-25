package io.fabric8.maven.docker.config;

import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Build configuration for health checks.
 */
public class HealthCheckConfiguration implements Serializable {

    // Default values are applied differently in build or runtime context, no default here
    private HealthCheckMode mode;

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
    
    /**
     * Use this method to apply a default mode depending on context (build or runtime)
     * @param mode The default mode to set
     * @return The configuration, making the call chainable
     */
    public HealthCheckConfiguration setModeIfNotPresent(HealthCheckMode mode) {
        if (this.mode == null) {
            this.mode = mode;
        }
        return this;
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
            case shell:
                if (cmd == null) {
                    throw new IllegalArgumentException("HealthCheck: the parameter 'cmd' is mandatory when the health check mode is set to 'cmd' (default) or 'shell'");
                }
                if (retries != null && retries < 0) {
                    throw new IllegalArgumentException("HealthCheck: the parameter 'retries' may not be negative");
                }
                if (interval != null && ! DurationParser.matchesDuration(interval)) {
                    throw new IllegalArgumentException("HealthCheck: illegal duration specified for interval");
                }
                if (timeout != null && ! DurationParser.matchesDuration(timeout)) {
                    throw new IllegalArgumentException("HealthCheck: illegal duration specified for timeout");
                }
                if (startPeriod != null && ! DurationParser.matchesDuration(startPeriod)) {
                    throw new IllegalArgumentException("HealthCheck: illegal duration specified for start period");
                }
                break;
        }
    }
    
    @Override
    public String toString() {
        return "HealthCheckConfiguration{" +
            "mode=" + mode +
            ", interval='" + interval + '\'' +
            ", timeout='" + timeout + '\'' +
            ", startPeriod='" + startPeriod + '\'' +
            ", retries=" + retries +
            ", cmd=" + cmd +
            '}';
    }
    
    // ===========================================

    public static class Builder {

        private final HealthCheckConfiguration config;

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
            return this.mode(mode != null ? HealthCheckMode.valueOf(mode) : null);
        }

        public Builder mode(HealthCheckMode mode) {
            config.mode = mode;
            return this;
        }

        public HealthCheckConfiguration build() {
            return config;
        }
    }
    
    public static final class DurationParser {
        
        // No instances allowed
        private DurationParser() {}
        
        /**
         * This complex regex allows duration in the special Docker format,
         * which is not ISO-8601 compatible, and thus not parseable directly.
         * (For example, it does not allow using days or even longer periods)
         * @implSpec See <a href="https://docs.docker.com/compose/compose-file/compose-file-v2/#specifying-durations">Docker Compose durations</a> for supported duration formats.
         *           <a href="https://docs.docker.com/engine/reference/builder/#healthcheck">Dockerfile HEALTHCHECK</a> has only very limited specification about allowed duration formatting.
         * @implNote Note that the Docker API requires nanosecond precision (int64/long).
         *           A conversion is easily done using {@link Duration#toNanos()}.
         *           Examples of allowed values: 23h17m1s, 10ms, 1s, 0h10ms, 1h2m1.3432s
         */
        private static final String durationRegex = "^((?<hours>0[1-9]|1[1-9]|2[1-3]|[0-9])h)?((?<mins>[0-5]?[0-9])m)?(((?<secs>[0-5]?[0-9])s)?((?<msecs>\\d{1,3})ms)?((?<usecs>\\d{1,3})us)?|(?<fsecs>[0-5]?[0-9])\\.(?<fraction>\\d{1,9})s)$";
        private static final Matcher durationMatcher = Pattern.compile(durationRegex).matcher("");
        
        public static boolean matchesDuration(String durationString) {
            if (durationString == null || durationString.isEmpty()) {
                return false;
            }
            return durationMatcher.reset(durationString).matches();
        }
        
        public static Duration parseDuration(String durationString) {
            if (durationString == null || durationString.isEmpty()) {
                return null;
            }
            
            if (durationMatcher.reset(durationString).matches()) {
                Duration duration = Duration.ZERO;
                // Add hours
                if (durationMatcher.group("hours") != null) {
                    duration = duration.plusHours(Long.parseLong(durationMatcher.group("hours")));
                }
                // Add minutes
                if (durationMatcher.group("mins") != null) {
                    duration = duration.plusMinutes(Long.parseLong(durationMatcher.group("mins")));
                }
                // When seconds are given with an optional fracture
                if (durationMatcher.group("fsecs") != null) {
                    duration = duration.plusSeconds(Long.parseLong(durationMatcher.group("fsecs")));
                    
                    String fraction = durationMatcher.group("fraction");
                    // Append enough zeros to make it a nanosecond precision, then tune the duration
                    fraction += StringUtils.repeat("0", 9 - fraction.length());
                    duration = duration.plusNanos(Long.parseLong(fraction));
                } else {
                    // Add seconds
                    if (durationMatcher.group("secs") != null) {
                        duration = duration.plusSeconds(Long.parseLong(durationMatcher.group("secs")));
                    }
                    // Add milliseconds
                    if (durationMatcher.group("msecs") != null) {
                        duration = duration.plusMillis(Long.parseLong(durationMatcher.group("msecs")));
                    }
                    // Add microseconds (make them fake nanoseconds first, as Duration does not support adding micros)
                    if (durationMatcher.group("usecs") != null) {
                        duration = duration.plusNanos(Long.parseLong(durationMatcher.group("usecs") + "000"));
                    }
                }
                return duration;
            }
            return null;
        }
    }
}
