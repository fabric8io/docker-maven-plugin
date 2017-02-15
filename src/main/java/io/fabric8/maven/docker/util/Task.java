package io.fabric8.maven.docker.util;

/**
 * Represents a generic task to be executed on a object.
 */
public interface Task<T> {

    void execute(T object) throws Exception;

}
