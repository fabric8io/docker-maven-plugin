package org.jolokia.docker.maven.access;

/**
 * Exception thrown if access to the docker host fails
 *
 * @author roland
 * @since 20.10.14
 */
public class DockerAccessException extends Exception {

    /**
     *
     * @param message error message
     * @param cause root cause
     */
    public DockerAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DockerAccessException(String message) {
        super(message);
    }
    
    public DockerAccessException(String format, Object...args) {
        super(String.format(format, args));
    }
}
