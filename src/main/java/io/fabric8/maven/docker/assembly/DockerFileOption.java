package io.fabric8.maven.docker.assembly;

/**
 * List of options for Docker keywords
 */
public enum DockerFileOption
{
    HEALTHCHECK_INTERVAL("interval"),
    HEALTHCHECK_TIMEOUT("timeout"),
    HEALTHCHECK_START_PERIOD("start-period"),
    HEALTHCHECK_RETRIES("retries");

    private String key;

    DockerFileOption(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    /**
     * Appends the option with the giv
     */
    public void addTo(StringBuilder sb, Object value) {
        sb.append("--");
        sb.append(getKey());
        sb.append("=");
        sb.append(value);
        sb.append(" ");
    }
}
