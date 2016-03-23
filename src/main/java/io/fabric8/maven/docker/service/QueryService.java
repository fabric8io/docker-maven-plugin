package io.fabric8.maven.docker.service;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.model.Network;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.util.AutoPullMode;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Query service for getting image and container information from the docker dameon
 *
 */
public class QueryService {

    // Default limit when listing containers
    private static final int CONTAINER_LIMIT = 100;

    // Access to docker daemon & logger
    private DockerAccess docker;
    private Logger log;

    /**
     * Constructor which gets its dependencies as args)
     *
     * @param docker remote access to docker daemon
     * @param log logger
     */
    public QueryService(DockerAccess docker, Logger log) {
        this.docker = docker;
        this.log = log;
    }

    /**
     * Get container by id
     *
     * @param containerId id
     * @return container
     * @throws DockerAccessException if an error occurs or no container with this id exists
     */
    public Container getContainer(String containerId) throws DockerAccessException {
        return docker.inspectContainer(containerId);
    }

    /**
     * Get a container running for a given container name.
     * @param containerName name of container to lookup
     * @return the container found or <code>null</code> if no container is available.
     * @throws DockerAccessException in case of an remote error
     */
    public Container getContainerByName(final String containerName) throws DockerAccessException {
        for (Container el : docker.listContainers(CONTAINER_LIMIT)) {
            if (containerName.equals(el.getName())) {
                return el;
            }
        }
        return null;
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
     * Get name for single container when the id is given
     *
     * @param containerId id of container to lookup
     * @return the name of the container
     * @throws DockerAccessException if access to the docker daemon fails
     */
    public String getContainerName(String containerId) throws DockerAccessException {
        return getContainer(containerId).getName();
    }

    /**
     * Get all containers which are build from an image. Only the last 100 containers are considered
     *
     * @param image for which its container are looked up
     * @return list of <code>Container</code> objects
     * @throws DockerAccessException if the request fails
     */
    public List<Container> getContainersForImage(final String image) throws DockerAccessException {
        List<Container> list = docker.listContainers(CONTAINER_LIMIT);
        List<Container> ret = new ArrayList<>();
        for (Container el : list) {
            if (image.equals(el.getImage())) {
                ret.add(el);
            }
        }
        return ret;
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

        for (Container container : getContainersForImage(image)) {
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
        return getContainerByName(containerName) != null;
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

    /**
     * Check whether an image needs to be pulled.
     *
     * @param mode the auto pull mode coming from the configuration
     * @param imageName name of the image to check
     * @param always whether to a alwaysPull mode would be active or is always ignored
     * @return
     * @throws DockerAccessException
     * @throws MojoExecutionException
     */
    public boolean imageRequiresAutoPull(String mode, String imageName, boolean always)
        throws DockerAccessException, MojoExecutionException {

        // The logic here is like this (see also #96):
        // If the image is not available and mode is either ON or ALWAYS --> pull
        // If mode == ALWAYS and no build config is available (so its a pulled-image anyway) --> pull
        // otherwise: don't pull
        AutoPullMode autoPullMode = AutoPullMode.fromString(mode);
        if (pullIfNotPresent(autoPullMode, imageName) ||
            alwaysPull(autoPullMode, always)) {
            return true;
        } else {
            if (!hasImage(imageName)) {
                throw new MojoExecutionException(
                        String.format("No image '%s' found, Please enable 'autoPull' or pull image '%s' yourself (docker pull %s)",
                                imageName, imageName, imageName));
            }
            return false;
        }
    }

    // Check whether ALWAYS is active
    private boolean alwaysPull(AutoPullMode autoPullMode, boolean always) {
        return always && autoPullMode == AutoPullMode.ALWAYS;
    }

    // Check if an image is not loaded but should be pulled
    private boolean pullIfNotPresent(AutoPullMode autoPull, String name) throws DockerAccessException {
        return autoPull.doPullIfNotPresent() && !hasImage(name);
    }
}
