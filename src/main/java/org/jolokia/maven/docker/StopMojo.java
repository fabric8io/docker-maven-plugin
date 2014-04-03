package org.jolokia.maven.docker;

import java.util.HashSet;
import java.util.Set;

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

    // Name of the image to stop. If none given, all are removed
    @Parameter(property = "docker.image", required = false)
    private String image;

    @Component
    MavenProject project;

    @Parameter(property = "docker.containerId")
    String containerId;

    @Parameter(property = "docker.keepContainer",defaultValue = "false")
    boolean keepContainer;

    @Parameter(property = "docker.keepRunning", defaultValue = "false")
    boolean keepRunning;

    protected void doExecute(DockerAccess access) throws MojoExecutionException, MojoFailureException {
        if (!keepRunning) {
            Set<String> ids = getContainerIds();
            for (String id : ids) {
                stopContainer(access, id);
            }
        }
    }

    private Set<String> getContainerIds() throws MojoFailureException {
        Set<String> ids = new HashSet<String>();
        if (containerId != null) {
            ids.add(containerId);
        } else {
            if (image != null) {
                Set<String> iIds = unregisterContainerId(image);
                if (iIds == null) {
                    throw new MojoFailureException("No container id given");
                }
                ids.addAll(iIds);
            } else {
                ids.addAll(unregisterAllContainer());
            }
        }
        return ids;
    }

    private void stopContainer(DockerAccess access, String id) throws MojoExecutionException {
        access.stopContainer(id);
        if (!keepContainer) {
            access.removeContainer(id);
        }
        info("Stopped " + id.substring(0,12) + (keepContainer ? "" : " and removed") + " container");
    }
}
