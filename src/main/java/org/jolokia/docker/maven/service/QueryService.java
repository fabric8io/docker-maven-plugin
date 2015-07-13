package org.jolokia.docker.maven.service;

import java.util.Iterator;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccess.ListArg;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.model.Container;
import org.jolokia.docker.maven.model.ContainerDetails;
import org.jolokia.docker.maven.model.Image;
import org.jolokia.docker.maven.util.AutoPullMode;
import org.jolokia.docker.maven.util.ImageName;
import org.jolokia.docker.maven.util.Logger;

public class QueryService {

    private static final ListArg LIMIT = ListArg.limit(100);

    private DockerAccess docker;
    
    private Logger log;

    public QueryService(DockerAccess docker, Logger log) {
        this.docker = docker;
        this.log = log;
    }
    
    public ContainerDetails getContainer(String containerId) throws DockerAccessException {
        return docker.inspectContainer(containerId);
    }

    public Container getContainerByName(final String name) throws DockerAccessException {
        List<Container> containers = filter(docker.listContainers(LIMIT), new Filter<Container>() {
            @Override
            public boolean matches(Container toFilter) {
                return !name.equals(toFilter.getName());
            }
        });
        
        return (containers.isEmpty()) ? null : containers.get(0);
    }

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
        return filter(docker.listContainers(LIMIT), new Filter<Container>() {
            @Override
            public boolean matches(Container toFilter) {
                return !image.equals(toFilter.getImage());
            }
        });
    }

    public Image getImage(String image) throws DockerAccessException {
        String name = new ImageName(image).getNameWithoutTag();
        return docker.listImages(ListArg.filter(name)).get(0);
    }
    
    /**
     * Finds the id of an image.
     * 
     * @param image name of the image.
     * @return the id of the image
     * @throws DockerAccessException if the request fails
     */
    public String getImageId(String image) throws DockerAccessException {
        return getImage(image).getId();
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

    public boolean hasContainerNamed(String name) throws DockerAccessException {
        return (getContainerByName(name) != null);
    }
    
    /**
     * Check whether the given Image is locally available.
     *
     * @param name name of image to check, the image can contain a tag, otherwise 'latest' will be appended
     * @return true if the image is locally available or false if it must be pulled.
     * @throws DockerAccessException if the request fails
     */
    public boolean hasImage(String name) throws DockerAccessException {
        ImageName imageName = new ImageName(name);

        String fullName = imageName.getFullName();
        String nameWithoutTag = imageName.getNameWithoutTag();

        for (Image image : docker.listImages(ListArg.filter(nameWithoutTag))) {
            if (image.getRepoTags().contains(fullName)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasRunningContainerNamed(String name) throws DockerAccessException{
        Container container = getContainerByName(name);
        return (container == null) ? false  : container.isRunning();
    }
    
    public boolean imageRequiresAutoPull(String mode, String name, String registry, boolean always)
        throws DockerAccessException, MojoExecutionException {

        // The logic here is like this (see also #96):
        // If the image is not available and mode is either ON or ALWAYS --> pull
        // If mode == ALWAYS and no build config is available (so its a pulled-image anyway) --> pull
        // otherwise: don't pull
        AutoPullMode autoPull = AutoPullMode.fromString(mode);
        boolean pullImage;
        if (pullIfNotPresent(autoPull, name) || alwaysAutoPull(autoPull, always)) {
            pullImage = true;
        } else {
            if (!hasImage(name)) {
                throw new MojoExecutionException(
                        String.format("No image '%s' found, Please enable 'autoPull' or pull image '%s'yourself (docker pull %s)",
                                name, name, name));
            }

            pullImage = false;
        }

        return pullImage;
    }

    public void initialize(DockerAccess docker, Logger log) {
        this.docker = docker;
        this.log = log;
    }

    /**
     * Determine if the container is running
     * 
     * @param containerId id of container to query
     * @return true if the container is running, false otherwise
     * @throws DockerAccessException if the request fails
     */
    public boolean isContainerRunning(String containerId) throws DockerAccessException {
        return docker.inspectContainer(containerId).getState().isRunning();
    }

    private boolean alwaysAutoPull(AutoPullMode autoPull, boolean always) {
        return (always && autoPull == AutoPullMode.ALWAYS);
    }

    private <T> List<T> filter(List<T> list, Filter<T> filter) {
        Iterator<T> iterator = list.iterator();

        while (iterator.hasNext()) {
            if (filter.matches(iterator.next())) {
                iterator.remove();
            }
        }

        return list;
    }
    

    private boolean pullIfNotPresent(AutoPullMode autoPull, String name) throws DockerAccessException {
        return (autoPull.doPullIfNotPresent() & !hasImage(name));
    }

    private interface Filter<T> {
        boolean matches(T toFilter);
    }
}
