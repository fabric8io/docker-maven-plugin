package org.jolokia.docker.maven.config;

/**
 * @author roland
 * @since 12.10.14
 */
public class LogConfiguration {

    public static final LogConfiguration DEFAULT = new LogConfiguration(false, null, null, null);

    /**
     * @parameter default-value="true"
     */
    private boolean enabled = true;

    /**
     * @parameter
     */
    private String prefix;

    /**
     * @parameter
     */
    private String date;

    /**
     * @parameter
     */
    private String color;

    public LogConfiguration() {}

    private LogConfiguration(boolean enabled, String prefix, String color, String date) {
        this.enabled = enabled;
        this.prefix = prefix;
        this.date = date;
        this.color = color;
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

    // =============================================================================

    public static class Builder {
        private boolean enabled = true;
        private String prefix, timestamp, color;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder color(String color) {
            this.color = color;
            return this;
        }

        public LogConfiguration build() {
            return new LogConfiguration(enabled, prefix, color, timestamp);
        }
    }
}
