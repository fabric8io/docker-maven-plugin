package io.fabric8.maven.docker.config;

import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author roland
 * @since 12.10.14
 */
public class LogConfiguration {

    public static final LogConfiguration DEFAULT = new LogConfiguration(false, null, null, null, null, null);

    @Parameter(defaultValue = "true")
    private boolean enabled = true;

    @Parameter
    private String prefix;

    @Parameter
    private String date;

    @Parameter
    private String color;

    @Parameter
    private String file;

    @Parameter
    private LogDriver driver;

    public LogConfiguration() {}

    private LogConfiguration(boolean enabled, String prefix, String color, String date, String file, LogDriver driver) {
        this.enabled = enabled;
        this.prefix = prefix;
        this.date = date;
        this.color = color;
        this.file = file;
        this.driver = driver;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getDate() {
        return date;
    }

    public String getColor() {
        return color;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getFileLocation() {
        return file;
    }

    public LogDriver getDriver() {
        return driver;
    }

    // =======================================================================================

    public static class LogDriver {

        /** @parameter */
        private String name;

        /** @parameter */
        private Map<String, String> opts;

        public LogDriver() {};

        private LogDriver(String name, Map<String, String> opts) {
            this.name = name;
            this.opts = opts;
        }

        public String getName() {
            return name;
        }

        public Map<String, String> getOpts() {
            return opts;
        }
    }

    // =============================================================================

    public static class Builder {
        private boolean enabled = true;
        private String prefix, date, color, file;
        private Map<String, String> driverOpts;
        private String driverName;
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder date(String date) {
            this.date = date;
            return this;
        }

        public Builder color(String color) {
            this.color = color;
            return this;
        }

        public Builder file(String file) {
            this.file = file;
            return this;
        }

        public Builder logDriverName(String logDriver) {
            this.driverName = logDriver;
            return this;
        }

        public Builder logDriverOpts(Map<String, String> logOpts) {
            this.driverOpts = logOpts;
            return this;
        }


        public LogConfiguration build() {
            return new LogConfiguration(enabled, prefix, color, date, file,
                                        driverName != null ? new LogDriver(driverName,driverOpts) : null);
        }
    }
}
