package io.fabric8.maven.docker.model;

import com.google.gson.JsonObject;

public class PortBindingException extends RuntimeException {
    public PortBindingException(String port, JsonObject portDetails) {
        super(String.format("Failed to create binding for port '%s'. Container ports: %s", port, portDetails));
    }
}
