package org.jolokia.docker.maven;

import java.io.File;
import java.util.*;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * Access to the <a href="http://docs.docker.io/en/latest/reference/api/docker_remote_api/">Docker API</a> which
 * provides the methods needed bu this maven plugin.
 *
 * @author roland
 * @since 04.04.14
 */
public interface DockerAccess {

    /**
     * Check whether the given Image is locally available.
     *
     * @param image name of image to check
     * @return true if the image is locally available or false if it must be pulled.
     * @throws MojoExecutionException in case of an request error
     */
    boolean hasImage(String image) throws MojoExecutionException;

    /**
     * Create a container from the given image.
     *
     * @param image the image from which the container should be created
     * @param ports ports to expose, can be null
     * @param command an optional command which gets executed when starting the container. might be null.
     * @return the container id
     * @throws MojoExecutionException if the container could not be created.
     */
    String createContainer(String image, Set<Integer> ports, String command) throws MojoExecutionException;

    /**
     * Start a container.
     *
     * @param containerId id of container to start
     * @param ports ports to map. The keys of this map must be ports which were exposed via {@link #createContainer(String, Set, String)}
     *              while the values are the host ports to use. If a value is <code>null</code> a port is dynamically selected
     *              by docker. The value of a dynamically selected port can be obtained via {@link #queryContainerPortMapping(String)}
     *              This map must not be null (but can be empty)
     * @param volumesFrom mount volumes from the given container id. Can be null.
     * @throws MojoExecutionException if the container could not be started.
     */
    void startContainer(String containerId, Map<Integer, Integer> ports, String volumesFrom) throws MojoExecutionException;

    /**
     * Stop a container.
     *
     * @param containerId the contaienr id
     * @throws MojoExecutionException if the container could not be stopped.
     */
    void stopContainer(String containerId) throws MojoExecutionException;

    /**
     * Query the port mappings for a certain container.
     *
     * @param containerId id of the container to query.
     * @return mapped ports where the keys of this map are the mapped host ports and the values are the container ports. The returned
     *         map is never null but can be empty.
     * @throws MojoExecutionException if the query failed.
     */
    Map<Integer, Integer> queryContainerPortMapping(String containerId) throws MojoExecutionException;

    /**
     * Get all containers which are build from an image. Only the last 100 containers are considered
     *
     * @param image for which its container are looked up
     * @return list of container ids
     * @throws MojoExecutionException if the request fails
     */
    List<String> getContainersForImage(String image) throws MojoExecutionException;

    /**
     * Remove a container with the given id
     *
     * @param containerId container id for the container to remove
     * @throws MojoExecutionException if the container couldn't be removed.
     */
    void removeContainer(String containerId) throws MojoExecutionException;

    /**
     * Pull an image from a remote registry and store it locally.
     *
     * @param image the image to pull.
     * @throws MojoExecutionException if the image couldn't be pulled.
     */
    void pullImage(String image) throws MojoExecutionException;

    /**
     * Lifecycle method for this access class which must be called before any other method is called.
     */
    void start();

    /**
     * Lifecycle method which must be called when this object is not needed anymore. This hook might be used for
     * cleaning up things.
     */
    void shutdown();

    /**
     * Create an docker image from a given archive
     *
     * @param image name of the image to build or <code>null</code> if none should be used
     * @param dockerArchive from which the docker image should be build
     */
    void buildImage(String image, File dockerArchive) throws MojoExecutionException;

    /**
     * Remove an image from this docker installation
     *
     * @param image image to remove
     */
    void removeImage(String image) throws MojoExecutionException;
}
