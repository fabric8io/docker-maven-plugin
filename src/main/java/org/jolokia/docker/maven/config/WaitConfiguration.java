package org.jolokia.docker.maven.config;

/**
 * @author roland
 * @since 12.10.14
 */
public class WaitConfiguration {

    /**
     * @parameter
     */
    private int time;

    /**
     * @parameter
     */
    private String url;

    private String method;
    /**
     * @parameter
     */
    private String log;

    /**
     * @parameter
     */
    private int shutdown;

    public WaitConfiguration() {}

    private WaitConfiguration(int time, String url, String method, String log, int shutdown) {
        this.time = time;
        this.url = url;
        this.method = method;
        this.log = log;
        this.shutdown = shutdown;
    }

    public int getTime() {
        return time;
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    public String getLog() {
        return log;
    }

    public int getShutdown() {
        return shutdown;
    }

    // =============================================================================

    public static class Builder {
        private int time = 0,shutdown = 0;
        private String url,log;
        private String method;

        public Builder time(int time) {
            this.time = time;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder log(String log) {
            this.log = log;
            return this;
        }

        public Builder shutdown(int shutdown) {
            this.shutdown = shutdown;
            return this;
        }

        public WaitConfiguration build() {
            return new WaitConfiguration(time,url,method,log,shutdown);
        }
    }
}
