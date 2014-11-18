package org.jolokia.docker.maven.config;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author roland
 * @since 12.10.14
 */
public class WaitConfiguration {

    @Parameter
    private int time;

    @Parameter
    private String url;

    @Parameter
    private String log;

    public WaitConfiguration() {}

    private WaitConfiguration(int time, String url, String log) {
        this.time = time;
        this.url = url;
        this.log = log;
    }

    public int getTime() {
        return time;
    }

    public String getUrl() {
        return url;
    }

    public String getLog() {
        return log;
    }

    // =============================================================================

    public static class Builder {
        private int time;
        private String url,log;

        public Builder time(int time) {
            this.time = time;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder log(String log) {
            this.log = log;
            return this;
        }

        public WaitConfiguration build() {
            return new WaitConfiguration(time,url,log);
        }
    }
}
