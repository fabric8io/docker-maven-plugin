package org.jolokia.maven.docker;

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

    @Parameter(property = "docker.containerId")
    String containerId;

    @Parameter(property = "docker.keepContainer",defaultValue = "false")
    boolean keepContainer;

    public void execute() throws MojoExecutionException, MojoFailureException {
        String id = containerId == null ?
                project.getProperties().getProperty(PROPERTY_CONTAINER_ID) :
                containerId;
        if (id == null) {
            throw new MojoFailureException("No container id given");
        }

        DockerAccess access = createDockerAccess();
        access.stopContainer(containerId);

        if (!keepContainer) {
            access.removeContainer(containerId);
        }

        info(">>> Docker - Stopped " + containerId + (keepContainer ? "" : " and removed") + " container");
    }
}
