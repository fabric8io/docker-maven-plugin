package io.fabric8.maven.docker.wait;

/**
 * Interface for various wait checkers
 *
 * @author roland
 * @since 25/03/2017
 */
public interface WaitChecker {
    /**
     * @return true if the the check has succeed, false otherwise
     */
    boolean check();

    /**
     * Cleanup hook which is called after the wait phase.
     */
    void cleanUp();
}
