package io.fabric8.maven.docker.access;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.fabric8.maven.docker.config.HealthCheckConfiguration;
import io.fabric8.maven.docker.config.HealthCheckConfiguration.DurationParser;

public class ContainerHealthCheckConfig {
    
    private final JsonObject healthcheck = new JsonObject();
    
    public ContainerHealthCheckConfig(HealthCheckConfiguration configuration) {
        JsonArray test = new JsonArray();
        switch (configuration.getMode()) {
            case none:
                test.add("NONE");
                break;
            case cmd:
                test.add("CMD");
                for (String arg : configuration.getCmd().asStrings()) {
                    test.add(arg);
                }
                break;
            case shell:
                test.add("CMD-SHELL");
                test.add(configuration.getCmd().getShell());
                break;
        }
        this.healthcheck.add("Test", test);
        
        if (configuration.getInterval() != null) {
            this.healthcheck.addProperty("Interval", DurationParser.parseDuration(configuration.getInterval()).toNanos());
        }
        if (configuration.getTimeout() != null) {
            this.healthcheck.addProperty("Timeout", DurationParser.parseDuration(configuration.getTimeout()).toNanos());
        }
        if (configuration.getStartPeriod() != null) {
            this.healthcheck.addProperty("StartPeriod", DurationParser.parseDuration(configuration.getStartPeriod()).toNanos());
        }
        if (configuration.getRetries() != null) {
            this.healthcheck.addProperty("Retries", configuration.getRetries());
        }
    }

    public String toJson() {
        return healthcheck.toString();
    }

    public JsonObject toJsonObject() {
        return healthcheck;
    }
}
