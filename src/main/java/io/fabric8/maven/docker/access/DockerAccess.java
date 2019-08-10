package io.fabric8.maven.docker.access;

import java.io.File;
import java.util.List;

import io.fabric8.maven.docker.access.log.LogCallback;
import io.fabric8.maven.docker.access.log.LogGetHandle;
import io.fabric8.maven.docker.config.ArchiveCompression;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.log.LogOutputSpec;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.model.ContainerDetails;
import io.fabric8.maven.docker.model.ExecDetails;
import io.fabric8.maven.docker.model.Image;
import io.fabric8.maven.docker.model.Network;

/**
 * Access to the <a href="http://docs.docker.io/en/latest/reference/api/docker_remote_api/">Docker API</a> which
 * provides the methods needed bu this maven plugin.
 *
 * @author roland
 * @since 04.04.14
 */
public interface DockerAccess {

    /**
     * Get the API version of the running server
     *
     * @return api version in the form "1.24"
     * @throws DockerAccessException if the api version could not be obtained
     */
    String getServerApiVersion() throws DockerAccessException;

    /**
     * Get a container
     *
     * @param containerIdOrName container id or name
     * @return <code>ContainerDetails<code> representing the container or null if none could be found
     * @throws DockerAccessException if the container could not be inspected
     */
    ContainerDetails getContainer(String containerIdOrName) throws DockerAccessException;

    /**
     * Get an exec container which is the result of executing a command in a running container.
     *
     * @param containerIdOrName exec container id or name
     * @return <code>ExecDetails<code> representing the container or null if none could be found
     * @throws DockerAccessException if the container could not be inspected
     */
    ExecDetails getExecContainer(String containerIdOrName) throws DockerAccessException;

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
     * List all containers from the Docker server.
     *
     * @param all whether to fetch also stopped containers. If false only running containers are returned
     * @return list of <code>Container</code> objects or an empty list if none is found
     * @throws DockerAccessException if the request fails
     */
    List<Container> listContainers(boolean all) throws DockerAccessException;

    /**
     * Get all containers which are build from an image. By default only the last containers are considered but this
     * can be tuned with a global parameters.
     *
     * @param image for which its container are looked up
     * @param all whether to fetch also stopped containers. If false only running containers are returned
     * @return list of <code>Container</code> objects or an empty list if none is found
     * @throws DockerAccessException if the request fails
     */
    List<Container> getContainersForImage(String image, boolean all) throws DockerAccessException;

    /**
     * Starts a previously set up exec instance (via {@link #createExecContainer(String, Arguments)} container
     * this API sets up a session with the exec command. Output is streamed to the log. This methods
     * returns only when the exec command has finished (i.e this method calls the command in a non-detached mode).
     *
     * @param containerId id of the exec container
     * @param outputSpec how to print out the output of the command
     * @throws DockerAccessException if the container could not be created.
     */
    void startExecContainer(String containerId, LogOutputSpec outputSpec) throws DockerAccessException;

    /**
     * Sets up an exec instance for a running container id
     *
     * @param containerId id of the running container which the exec container will be created for
     * @param arguments container exec commands to run
     * @throws DockerAccessException if the container could not be created.
     */
    String createExecContainer(String containerId, Arguments arguments) throws DockerAccessException;

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
     * @param killWait the time to wait between stop and kill (in seconds)
     * @throws DockerAccessException if the container could not be stopped.
     */
    void stopContainer(String containerId, int killWait) throws DockerAccessException;

    /** Copy an archive (must be a tar) into a running container
     * Get all containers matching a certain label. This might not be a cheap operation especially if many containers
     * are running. Use with care.
     *
     * @param containerId container to copy into
     * @param archive local archive to copy into
     * @param targetPath target path to use
     * @throws DockerAccessException if the archive could not be copied
     */
    void copyArchive(String containerId, File archive, String targetPath)
            throws DockerAccessException;

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
     * List the containers on the server
     * @param all if true, return untagged images
     * @return the images list (may be empty but never null)
     * @throws DockerAccessException if the list couldn't be retrieved
     */
    List<Image> listImages(boolean all) throws DockerAccessException;

    /**
     * Load an image from an archive.
     *
     * @param image the image to pull.
     * @param tarArchive archive file
     * @throws DockerAccessException if the image couldn't be loaded.
     */
    void loadImage(String image, File tarArchive) throws DockerAccessException;

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
     * @param retries optional number of times the push should be retried on a 500 error
     * @throws DockerAccessException in case pushing fails
     */
    void pushImage(String image, AuthConfig authConfig, String registry, int retries) throws DockerAccessException;

    /**
     * Create an docker image from a given archive
     *
     * @param image name of the image to build or <code>null</code> if none should be used
     * @param dockerArchive from which the docker image should be build
     * @param options additional query arguments to add when building the image. Can be null.
     * @throws DockerAccessException if docker host reports an error during building of an image
     */
    void buildImage(String image, File dockerArchive, BuildOptions options) throws DockerAccessException;

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
     * Save an image to a tar file
     *
     * @param image image to save
     * @param filename target filename
     * @param compression compression to use for the archive
     * @throws DockerAccessException if an image cannot be removed
     */
    void saveImage(String image, String filename, ArchiveCompression compression) throws DockerAccessException;

    /**
     * List all networks
     *
     * @return list of <code>Network<code> objects
     * @throws DockerAccessException if the networks could not be listed
     */
    List<Network> listNetworks() throws DockerAccessException;

    /**
     * Create a custom network from the given configuration.
     *
     * @param configuration network configuration
     * @throws DockerAccessException if the container could not be created.
     */

    String createNetwork(NetworkCreateConfig configuration) throws DockerAccessException;

    /**
     * Remove a custom network
     *
     * @param networkId network to remove
     * @return true if an network was removed, false if none was removed
     * @throws DockerAccessException if an image cannot be removed
     */
    boolean removeNetwork(String networkId) throws DockerAccessException;

    /**
     * Lifecycle method for this access class which must be called before any other method is called.
     */
    void start() throws DockerAccessException;

    /**
     * Lifecycle method which must be called when this object is not needed anymore. This hook might be used for
     * cleaning up things.
     */
    void shutdown();

   /**
    *  Create a volume
    *
    *  @param configuration volume configuration
    *  @return the name of the Volume
    *  @throws DockerAccessException if the volume could not be created.
    */
   String createVolume(VolumeCreateConfig configuration) throws DockerAccessException;

   /**
    * Removes a volume. It is a no-op if the volume does not exist.
    * @param name volume name to remove
    * @throws DockerAccessException if the volume could not be removed
    */
   void removeVolume(String name) throws DockerAccessException;
}
