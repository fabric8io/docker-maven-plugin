package io.fabric8.maven.docker.config;


public enum HealthCheckMode {

    /**
     * Mainly used to disable any health check provided by the base image.
     * This mode is supported at build and run time.
     */
    none,

    /**
     * A command-based health check.
     * This mode is supported at build and run time.
     */
    cmd,
    
    /**
     * A shell-wrapped command-based health check.
     * This mode is supported at runtime only.
     */
    shell,
    
    /**
     * Runtime-only mode, used to change options, but not the test itself
     */
    inherit

}
