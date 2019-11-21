package io.fabric8.maven.docker.config;

/*
 * Enum for holding information regarding stopping containers in dmp
 */
public enum StopMode {
    /**
     * Would wait for the process to terminate correctly
     */
    graceful,

    /**
     * Would terminate the process with a sigkill
     */
    kill
}
