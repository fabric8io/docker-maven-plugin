package org.jolokia.docker.maven.access;

import java.io.File;
import java.util.*;

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
     * @throws DockerAccessException in case of an request error
     */
    boolean hasImage(String image) throws DockerAccessException;

    /**
     * Create a container from the given image.
     *
     * @param image the image from which the container should be created
     * @param command an optional command which gets executed when starting the container. might be null.
     * @param ports ports to expose, can be null
     * @param env map with environment variables to use
     * @return the container id
     * @throws DockerAccessException if the container could not be created.
     */
    String createContainer(String image, String command, Set<Integer> ports, Map<String, String> env) throws DockerAccessException;

    /**
     * Get the the name of a container for a given container id
     *
     * @param id container id to lookup
     * @return name of the container
     * @throws DockerAccessException if the id does not match a container
     */
    String getContainerName(String id) throws DockerAccessException;

    /**
     * Start a container.
     *
     * @param containerId id of container to start
     * @param ports ports to map. The keys of this map must be ports which were exposed via {@link #createContainer(String, String, Set, Map)}
     *              while the values are the host ports to use. If a value is <code>null</code> a port is dynamically selected
     *              by docker. The value of a dynamically selected port can be obtained via {@link #queryContainerPortMapping(String)}
     *              This map must not be null (but can be empty)
     * @param bindTo bind to addresses. The keys of this map are container ports and the values are the local ip addresses they should be
     * 				 bound to.
     * @param volumesFrom mount volumes from the given container id. Can be null.
     * @throws DockerAccessException if the container could not be started.
     */
    void startContainer(String containerId, Map<Integer, Integer> ports, Map<Integer, String> bindTo, List<String> volumesFrom, List<String> links) throws DockerAccessException;

    /**
     * Stop a container.
     *
     * @param containerId the contaienr id
     * @throws DockerAccessException if the container could not be stopped.
     */
    void stopContainer(String containerId) throws DockerAccessException;

    /**
     * Query the port mappings for a certain container.
     *
     * @param containerId id of the container to query.
     * @return mapped ports where the keys of this map are the mapped host ports and the values are the container ports. The returned
     *         map is never null but can be empty.
     * @throws DockerAccessException if the query failed.
     */
    Map<Integer, Integer> queryContainerPortMapping(String containerId) throws DockerAccessException;

    /**
     * Get all containers which are build from an image. Only the last 100 containers are considered
     *
     * @param image for which its container are looked up
     * @return list of container ids
     * @throws DockerAccessException if the request fails
     */
    List<String> getContainersForImage(String image) throws DockerAccessException;

    /**
     * Get logs for a container.
     *
     * @param containerId container id
     * @return the logs for the given container
     * @throws DockerAccessException if the request fails
     */
    String getLogs(String containerId) throws DockerAccessException;

    /**
     * Remove a container with the given id
     *
     * @param containerId container id for the container to remove
     * @throws DockerAccessException if the container couldn't be removed.
     */
    void removeContainer(String containerId) throws DockerAccessException;

    /**
     * Pull an image from a remote registry and store it locally.
     *
     * @param image the image to pull.
     * @param authConfig authentication configuration used when pulling an image
     * @throws DockerAccessException if the image couldn't be pulled.
     */
    void pullImage(String image, AuthConfig authConfig) throws DockerAccessException;

    /**
     * Push an image to a registry.
     * @param image image name to push (can be an alias)
     * @throws DockerAccessException in case pushing fails
     */
    void pushImage(String image, AuthConfig authConfig) throws DockerAccessException;

    /**
     * Create an docker image from a given archive
     *
     * @param image name of the image to build or <code>null</code> if none should be used
     * @param dockerArchive from which the docker image should be build
     * @throws DockerAccessException if dockerhost reporst an error during building of an image
     */
    void buildImage(String image, File dockerArchive) throws DockerAccessException;

    /**
     * Remove an image from this docker installation
     *
     * @param image image to remove
     * @param force if set to true remove containers as well (only the first vararg is evaluated)
     * @return true if an image was removed, false if none was removed
     * @throws DockerAccessException if an image cannot be removed
     */
    boolean removeImage(String image, boolean ... force) throws DockerAccessException;

    /**
     * Lifecycle method for this access class which must be called before any other method is called.
     */
    void start() throws DockerAccessException;

    /**
     * Lifecycle method which must be called when this object is not needed anymore. This hook might be used for
     * cleaning up things.
     */
    void shutdown();
}
