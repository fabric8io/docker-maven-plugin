package io.fabric8.maven.docker.config.run;

import java.io.Serializable;
import java.util.Map;

import io.fabric8.maven.docker.config.build.BuildConfiguration;
import org.apache.commons.lang3.SerializationUtils;

/**
 * @author roland
 * @since 12.10.14
 */
public class LogConfiguration implements Serializable {

    public static final LogConfiguration DEFAULT = new LogConfiguration();

    private Boolean enabled;

    private String prefix;

    private String date;

    private String color;

    private String file;

    private LogDriver driver;

    public LogConfiguration() {}

    public String getPrefix() {
        return prefix;
    }

    public String getDate() {
        return date;
    }

    public String getColor() {
        return color;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    /**
     * If explicitly enabled, or configured in any way and NOT explicitly disabled, return true.
     *
     * @return
     */
    public boolean isActivated() {
        return enabled == Boolean.TRUE ||
                (enabled != Boolean.FALSE && !isBlank());
    }

    /**
     * Returns true if all options (except enabled) are null, used to decide value of enabled.
     *
     * @return
     */
    private boolean isBlank() {
        return prefix == null && date == null && color == null && file == null && driver == null;
    }

    public String getFileLocation() {
        return file;
    }

    public LogDriver getDriver() {
        return driver;
    }

    // =======================================================================================

    public static class LogDriver implements Serializable {

        private String name;

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

        private final LogConfiguration config;

        private String driverName;
        private Map<String, String> driverOpts;

        public Builder() {
            this(null);
        }

        public Builder(LogConfiguration that) {
            if (that == null) {
                this.config = new LogConfiguration();
            } else {
                this.config = SerializationUtils.clone(that);
            }
        }

        public Builder enabled(Boolean enabled) {
            config.enabled = enabled;
            return this;
        }

        public Builder prefix(String prefix) {
            config.prefix = prefix;
            return this;
        }

        public Builder date(String date) {
            config.date = date;
            return this;
        }

        public Builder color(String color) {
            config.color = color;
            return this;
        }

        public Builder file(String file) {
            config.file = file;
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
            if (driverName != null) {
                config.driver = new LogDriver(driverName, driverOpts);
            }
            return config;
        }
    }
}
