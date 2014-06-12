package org.jolokia.docker.maven;

import java.util.*;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

/**
 * @author roland
 * @since 26.03.14
 */
@Mojo(name = "stop", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class StopMojo extends AbstractDockerMojo {

    @Component
    MavenProject project;

    // Name of the image for which to stop its containers. If none given, all are removed
    @Parameter(property = "docker.image", required = false)
    private String image;

    // The container id to stop. If not given, the containers started with 'docker:start' are stopped
    @Parameter(property = "docker.containerId")
    String containerId;

    // Whether to keep the containers afters stopping
    @Parameter(property = "docker.keepContainer",defaultValue = "false")
    boolean keepContainer;

    // Whether to *not* stop the container. Mostly useful as a command line param
    @Parameter(property = "docker.keepRunning", defaultValue = "false")
    boolean keepRunning;

    // Whether the data container & image should be kept if an assembly is used
    @Parameter(property = "docker.keepData", defaultValue = "false")
    private boolean keepData;

    protected void executeInternal(DockerAccess access) throws MojoExecutionException, MojoFailureException {
        if (!keepRunning) {
            stopAndRemoveContainers(access);
            if (!keepData) {
                cleanupData(access);
            }
        }
    }

    private void stopAndRemoveContainers(DockerAccess access) throws MojoFailureException, MojoExecutionException {
        Set<String> ids = getContainersToRemove();
        for (String id : ids) {
            access.stopContainer(id);
            if (!keepContainer) {
                access.removeContainer(id);
            }
            info("Stopped " + id.substring(0, 12) + (keepContainer ? "" : " and removed") + " container");
        }
    }


    private void cleanupData(DockerAccess access) throws MojoExecutionException {
        Set<String> imageIds = getDataImagesForImage(image);
        for (String id : imageIds) {
            List<String> containers = access.getContainersForImage(id);
            for (String container : containers) {
                access.removeContainer(container);
                info("Removed data container " + container);
            }
            access.removeImage(id);
            info("Removed data image " + id);
        }
    }

    private Set<String> getContainersToRemove() throws MojoFailureException {
        return containerId != null ?
                Collections.singleton(containerId) :
                getContainersForImage(image);
    }

}
