package org.jolokia.docker.maven.config;

public class WatchConfiguration {

    /**
     * @parameter
     */
    private int interval;

    public WatchConfiguration() {
    }

    public WatchConfiguration(int interval) {
        this.interval = interval;
    }

    public int getInterval() {
        return interval;
    }

    public static class Builder {

        private int time = 1000;

        public Builder time(int time) {
            this.time = time;
            return this;
        }

        public WatchConfiguration build() {
            return new WatchConfiguration(time);
        }
    }


}
