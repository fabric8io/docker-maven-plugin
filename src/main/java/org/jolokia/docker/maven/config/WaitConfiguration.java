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
     * @deprecated Use <http><url></url></http> instead
     */
    private String url;

    /**
     * @parameter
     */
    private HttpConfiguration http;

    /**
     * @parameter
     */
    private ExecConfiguration exec;

    /**
     * @parameter
     */
    private String log;

    /**
     * @parameter
     */
    private int shutdown;

    public WaitConfiguration() {}

    private WaitConfiguration(int time, ExecConfiguration exec, HttpConfiguration http, String log, int shutdown) {
        this.time = time;
        this.exec = exec;
        this.http = http;
        this.log = log;
        this.shutdown = shutdown;
    }

    public int getTime() {
        return time;
    }

    public String getUrl() {
        return http != null ? http.getUrl() : url;
    }

    public ExecConfiguration getExec() {
        return exec;
    }

    public HttpConfiguration getHttp() {
        return http;
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
        private String url,log,status;
        private String method;
        private String preStop;
        private String postStart;

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

        public Builder status(String status) {
            this.status = status;
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
            return new WaitConfiguration(time, new ExecConfiguration(postStart, preStop), new HttpConfiguration(url,method,status), log, shutdown);
        }

        public Builder preStop(String command) {
            this.preStop = command;
            return this;
        }

        public Builder postStart(String command) {
            this.postStart = command;
            return this;
        }
    }

    public static class ExecConfiguration {
        /** @parameter */
        private String postStart;
        /** @parameter */
        private String preStop;

        public ExecConfiguration() {
        }

        public ExecConfiguration(String postStart, String preStop) {
            this.postStart = postStart;
            this.preStop = preStop;
        }

        public String getPostStart() {
            return postStart;
        }

        public String getPreStop() {
            return preStop;
        }
    }

    public static class HttpConfiguration {

        /** @parameter */
        private String url;

        /** @parameter */
        private String method;

        /** @parameter */
        private String status;

        public HttpConfiguration() {}

        private HttpConfiguration(String url, String method, String status) {
            this.url = url;
            this.method = method;
        }

        public String getUrl() {
            return url;
        }

        public String getMethod() {
            return method;
        }

        public String getStatus() {
            return status;
        }
    }
}
