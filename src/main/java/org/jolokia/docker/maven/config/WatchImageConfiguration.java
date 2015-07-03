package org.jolokia.docker.maven.config;

/**
 * Configuration for watching on image changes
 *
 */
public class WatchImageConfiguration {

    /**
     * @parameter
     */
    private int interval;

    /**
     * @parameter
     */
    private WatchMode mode;

    /**
     * @parameter
     */
    private String postGoal;

    public WatchImageConfiguration() {
    }

    public int getInterval() {
        return interval;
    }

    public WatchMode getMode() {
        return mode;
    }

    public String getPostGoal() {
        return postGoal;
    }

    public static class Builder {

        private int time = 5000;

        private WatchImageConfiguration c = new WatchImageConfiguration();

        public Builder interval(int interval) {
            c.interval = interval;
            return this;
        }

        public Builder mode(String mode) {
            if (mode != null) {
                c.mode = WatchMode.valueOf(mode.toLowerCase());
            }
            return this;
        }

        public Builder postGoal(String goal) {
            c.postGoal = goal;
            return this;
        }

        public WatchImageConfiguration build() {
            return c;
        }
    }


}
