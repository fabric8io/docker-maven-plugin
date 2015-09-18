package org.jolokia.docker.maven.access;

import java.io.File;
import java.util.List;

import org.jolokia.docker.maven.access.log.LogCallback;
import org.jolokia.docker.maven.access.log.LogGetHandle;
import org.jolokia.docker.maven.config.Arguments;
import org.jolokia.docker.maven.model.*;

/**
 * Access to the <a href="http://docs.docker.io/en/latest/reference/api/docker_remote_api/">Docker API</a> which
 * provides the methods needed bu this maven plugin.
 *
 * @author roland
 * @since 04.04.14
 */
public interface DockerAccess {

    /**
     * Inspect a container
     * 
     * @param containerId container id
     * @return <code>ContainerDetails<code> representing the container
     * @throws DockerAccessException if the container could not be inspected
     */
    Container inspectContainer(String containerId) throws DockerAccessException;

    /**
     * Check whether the given name exists as image at the docker daemon
     *
     * @param name image name to check
     * @return true if the image exists
     */
    boolean hasImage(String name) throws DockerAccessException;

    /**
     * Get the image id of a given name or <code>null</code> if no such image exists
     *
     * @param name name to lookup
     * @return the image id or <code>null</code>
     */
    String getImageId(String name) throws DockerAccessException;

    /**
     * List containers
     * 
     * @param limit limit of containers to list
     * @return list of <code>Container<code> objects
     * @throws DockerAccessException if the containers could not be listed
     */
    List<Container> listContainers(int limit) throws DockerAccessException;

    /**
     * Starts a previously set up exec instance id.
     * this API sets up an interactive session with the exec command
     *
     * @param containerId id of the exec container
     * @return stdout/stderr of running the exec container
     * @throws DockerAccessException if the container could not be created.
     */
    String startExecContainer(String containerId) throws DockerAccessException;

    /**
     * Sets up an exec instance for a running container id
     *
     * @param arguments container exec commands to run
     * @param containerId id of the running container which the exec container will be created for
     * @throws DockerAccessException if the container could not be created.
     */
    String createExecContainer(Arguments arguments, String containerId) throws DockerAccessException;

    /**
     * Create a container from the given image.
     *
     * <p>The <code>container id</code> will be set on the <code>container</code> upon successful creation.</p>
     *
     * @param configuration container configuration
     * @param containerName name container should be created with or <code>null</code> for a docker provided name
     * @throws DockerAccessException if the container could not be created.
     */
    String createContainer(ContainerCreateConfig configuration, String containerName) throws DockerAccessException;

    /**
     * Start a container.
     *
     * @param containerId id of the container to start
     * @throws DockerAccessException if the container could not be started.
     */
    void startContainer(String containerId) throws DockerAccessException;

    /**
     * Stop a container.
     *
     * @param containerId the container id
     * @param killAfterStopPeriod the time to wait between stop and kill (in seconds)
     * @throws DockerAccessException if the container could not be stopped.
     */
    void stopContainer(String containerId, int killAfterStopPeriod) throws DockerAccessException;

    /**
     * Get logs for a container up to now synchronously.
     *
     * @param containerId container id
     * @param callback which is called for each line received
     */
    void getLogSync(String containerId, LogCallback callback);

    /**
     * Get logs asynchronously. This call will start a thread in the background for doing the request.
     * It returns a handle which can be used to abort the request on demand.
     *
     * @param containerId id of the container for which to fetch the logs
     * @param callback to call when log data arrives
     * @return handle for managing the lifecycle of the thread
     */
    LogGetHandle getLogAsync(String containerId, LogCallback callback);

    /**
     * Remove a container with the given id
     *
     * @param containerId container id for the container to remove
     * @param removeVolumes if true, will remove any volumes associated to container
     * @throws DockerAccessException if the container couldn't be removed.
     */
    void removeContainer(String containerId, boolean removeVolumes) throws DockerAccessException;

    /**
     * Pull an image from a remote registry and store it locally.
     *
     * @param image the image to pull.
     * @param authConfig authentication configuration used when pulling an image
     * @param registry an optional registry from where to pull the image. Can be null.
     * @throws DockerAccessException if the image couldn't be pulled.
     */
    void pullImage(String image, AuthConfig authConfig, String registry) throws DockerAccessException;

    /**
     * Push an image to a registry. An registry can be specified which is used as target
     * if the image name the image does not contain a registry.
     *
     * If an optional registry is used, the image is also tagged with the full name containing the registry as
     * part (if not already existent)
     *
     * @param image image name to push
     * @param authConfig authentication configuration
     * @param registry optional registry to which the image should be pushed.
     * @throws DockerAccessException in case pushing fails
     */
    void pushImage(String image, AuthConfig authConfig, String registry) throws DockerAccessException;

    /**
     * Create an docker image from a given archive
     *
     * @param image name of the image to build or <code>null</code> if none should be used
     * @param dockerArchive from which the docker image should be build
     * @param forceRemove whether to remove intermediate containers
     * @throws DockerAccessException if docker host reports an error during building of an image
     */
    void buildImage(String image, File dockerArchive, boolean forceRemove) throws DockerAccessException;

    /**
     * Alias an image in the repository with a complete new name. (Note that this maps to a Docker Remote API 'tag'
     * operation, which IMO is badly named since it also can generate a complete alias to a given image)
     *
     * @param sourceImage full name (including tag) of the image to alias
     * @param targetImage the alias name
     * @param force forced tagging
     * @throws DockerAccessException if the original image doesn't exist or another error occurs somehow.
     */
    void tag(String sourceImage, String targetImage, boolean force) throws DockerAccessException;

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
