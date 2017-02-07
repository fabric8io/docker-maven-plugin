package io.fabric8.maven.docker.util;

import io.fabric8.maven.docker.access.DockerAccessException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Represents a generic task to be executed on a object.
 */
public interface Task<T> {

    void execute(T object) throws DockerAccessException, MojoExecutionException, MojoFailureException;

}
