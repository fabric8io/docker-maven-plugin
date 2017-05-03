package io.fabric8.maven.docker.wait;

/**
 * Wait for a certain amount of time
 *
 * @author roland
 * @since 25/03/2017
 */
public class PreconditionFailedException extends Exception {
    private final long waited;

    PreconditionFailedException(String message, long waited) {
        super(message);
        this.waited = waited;
    }

    public long getWaited() {
        return waited;
    }
}
