package io.fabric8.maven.docker.config;


public enum HealthCheckMode {

    /**
     * Mainly used to disable any health check provided by the base image.
     */
    none,

    /**
     * A command based health check.
     */
    cmd;

}
