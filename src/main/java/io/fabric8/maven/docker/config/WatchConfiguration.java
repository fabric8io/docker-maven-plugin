package io.fabric8.maven.docker.config;

import java.io.Serializable;

import org.apache.commons.lang3.SerializationUtils;

/**
 * Configuration for watching on image changes
 */
public class WatchConfiguration implements Serializable {

    private Integer interval;

    private WatchMode mode;

    private String postGoal;

    private String postExec;

    public WatchConfiguration() {};

    public int getInterval() {
        return interval != null ? interval : 5000;
    }

    public Integer getIntervalRaw() {
        return interval;
    }

    public WatchMode getMode() {
        return mode;
    }

    public String getPostGoal() {
        return postGoal;
    }

    public String getPostExec() {
        return postExec;
    }

    public static class Builder {

        private final WatchConfiguration c;

        public Builder() {
            this(null);
        }

        public Builder(WatchConfiguration that) {
            if (that == null) {
                this.c = new WatchConfiguration();
            } else {
                this.c = SerializationUtils.clone(that);
            }
        }

        public Builder interval(Integer interval) {
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

        public Builder postExec(String exec) {
            c.postExec = exec;
            return this;
        }

        public WatchConfiguration build() {
            return c;
        }
    }


}
