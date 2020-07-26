package io.fabric8.maven.docker.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.model.Image;
import io.fabric8.maven.docker.model.Network;

/**
 * Query service for getting image and container information from the docker dameon
 *
 */
public class QueryService {

    // Access to docker daemon & logger
    private DockerAccess docker;

    /**
     * Constructor which gets its dependencies as args)
     *  @param docker remote access to docker daemon
     * */
    public QueryService(DockerAccess docker) {
        this.docker = docker;
    }

    /**
     * Get container by id
     *
     * @param containerIdOrName container id or name
     * @return container found
     * @throws DockerAccessException if an error occurs or no container with this id or name exists
     */
    public Container getMandatoryContainer(String containerIdOrName) throws DockerAccessException {
        Container container = getContainer(containerIdOrName);
        if (container == null) {
            throw new DockerAccessException("Cannot find container %s", containerIdOrName);
        }
        return container;
    }

    /**
     * Get a container running for a given container name.
     * @param containerIdOrName name of container to lookup
     * @return the container found or <code>null</code> if no container is available.
     * @throws DockerAccessException in case of an remote error
     */
    public Container getContainer(final String containerIdOrName) throws DockerAccessException {
        return docker.getContainer(containerIdOrName);
    }

    /**
     * Get a network for a given network name.
     * @param networkName name of network to lookup
     * @return the network found or <code>null</code> if no network is available.
     * @throws DockerAccessException in case of an remote error
     */
    public Network getNetworkByName(final String networkName) throws DockerAccessException {
        for (Network el : docker.listNetworks()) {
            if (networkName.equals(el.getName())) {
                return el;
            }
        }
        return null;
    }

    /**
     * Get all networks.
     * @return the network found or <code>null</code> if no network is available.
     * @throws DockerAccessException in case of an remote error
     */
    public Set<Network> getNetworks() throws DockerAccessException {
        return new HashSet<>(docker.listNetworks());
    }

    /**
     * Get name for single container when the id is given
     *
     * @param containerId id of container to lookup
     * @return the name of the container
     * @throws DockerAccessException if access to the docker daemon fails
     */
    public String getContainerName(String containerId) throws DockerAccessException {
        return getMandatoryContainer(containerId).getName();
    }

    /**
     * List all containers on the Docker server.
     *
     * @param all if true, list stopped containers as well as running containers.
     *
     * @return list of <code>Container</code> objects
     * @throws DockerAccessException if the request fails
     */
    public List<Container> listContainers(final boolean all) throws DockerAccessException {
        return docker.listContainers(all);
    }

    /**
     * Get all containers which are build from an image. By default only the last containers are considered but this
     * can be tuned with a global parameters.
     *
     * @param image for which its container are looked up
     * @param all if true, fetch stopped containers as well as running containers.
     * @return list of <code>Container</code> objects
     * @throws DockerAccessException if the request fails
     */
    public List<Container> getContainersForImage(final String image, final boolean all) throws DockerAccessException {
        return docker.getContainersForImage(image, all);
    }

    /**
     * Get all images on the Docker server.
     *
     * @param all if true, fetch untagged images as well as tagged.
     *
     * @return list of <code>Image</code> objects
     * @throws DockerAccessException if the request fails
     */
    public List<Image> listImages(final boolean all) throws DockerAccessException {
        return docker.listImages(all);
    }

    /**
     * Finds the id of an image.
     *
     * @param imageName name of the image.
     * @return the id of the image
     * @throws DockerAccessException if the request fails
     */
    public String getImageId(String imageName) throws DockerAccessException {
        return docker.getImageId(imageName);
    }

    /**
     * Get the id of the latest container started for an image
     *
     * @param image for which its container are looked up
     * @return container or <code>null</code> if no container has been started for this image.
     * @throws DockerAccessException if the request fails
     */
    public Container getLatestContainerForImage(String image) throws DockerAccessException {
        long newest = 0;
        Container result = null;

        for (Container container : getContainersForImage(image, false)) {
            long timestamp = container.getCreated();

            if (timestamp < newest) {
                continue;
            }

            newest = timestamp;
            result = container;
        }

        return result;
    }

    /**
     * Check whether a container with the given name exists
     *
     * @param containerName container name
     * @return true if a container with this name exists, false otherwise.
     * @throws DockerAccessException
     */
    public boolean hasContainer(String containerName) throws DockerAccessException {
        return getContainer(containerName) != null;
    }

    /**
     * Check whether a network with the given name exists
     *
     * @param networkName network name
     * @return true if a network with this name exists, false otherwise.
     * @throws DockerAccessException
     */

    public boolean hasNetwork(String networkName) throws DockerAccessException {
        return getNetworkByName(networkName) != null;
    }

    /**
     * Check whether the given Image is locally available.
     *
     * @param name name of image to check, the image can contain a tag, otherwise 'latest' will be appended
     * @return true if the image is locally available or false if it must be pulled.
     * @throws DockerAccessException if the request fails
     */
    public boolean hasImage(String name) throws DockerAccessException {
        return docker.hasImage(name);
    }

}
