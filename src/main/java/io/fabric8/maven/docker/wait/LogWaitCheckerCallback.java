package io.fabric8.maven.docker.wait;


/**
 * Interface called during waiting on log when a log line matches
 */
public interface LogWaitCheckerCallback {
    void matched();
}
