package io.fabric8.maven.docker.wait;

import java.util.concurrent.TimeoutException;

/**
 * Wait for a certain amount of time
 *
 * @author roland
 * @since 25/03/2017
 */
public class NotRunningException extends Exception {
    private final long waited;

    NotRunningException(String message, long waited) {
        super(message);
        this.waited = waited;
    }

    public long getWaited() {
        return waited;
    }
}
