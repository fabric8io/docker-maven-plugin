package io.fabric8.maven.docker.access;

import java.io.IOException;

/**
 * Exception thrown if access to the docker host fails
 *
 * @author roland
 * @since 20.10.14
 */
public class DockerAccessException extends IOException {

    /**
     * Constructor
     *
     * @param cause root cause
     * @param message error message
     */
    public DockerAccessException(Throwable cause, String message) {
        super(message, cause);
    }

    public DockerAccessException(String message) {
        super(message);
    }
    
    public DockerAccessException(String format, Object...args) {
        super(String.format(format, args));
    }

    public DockerAccessException(Throwable cause, String format, Object ... args) {
        super(String.format(format, args),cause);
    }
}
