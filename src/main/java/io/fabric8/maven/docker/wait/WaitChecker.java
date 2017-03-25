package io.fabric8.maven.docker.wait;

/**
 * @author roland
 * @since 25/03/2017
 */
public interface WaitChecker {
    boolean check();

    void cleanUp();
}
