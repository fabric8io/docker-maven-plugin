package org.jolokia.docker.maven;

import java.util.*;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.config.ImageConfiguration;

/**
 * @author roland
 * @since 26.03.14
 */
@Mojo(name = "stop", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class StopMojo extends AbstractDockerMojo {

    // Whether to keep the containers afters stopping
    @Parameter(property = "docker.keepContainer",defaultValue = "false")
    private boolean keepContainer;

    // Whether to *not* stop the container. Mostly useful as a command line param
    @Parameter(property = "docker.keepRunning", defaultValue = "false")
    private boolean keepRunning;

    // Whether the data container & image should be kept if an assembly is used
    @Parameter(property = "docker.keepData", defaultValue = "false")
    private boolean keepData;

    protected void executeInternal(DockerAccess access) throws MojoExecutionException, DockerAccessException {
        if (!keepRunning) {
            getLog().info("about to stop " + this.images.size() + " docker image(s).");

            Set<ShutdownAction> actions = Collections.synchronizedSet(new LinkedHashSet<ShutdownAction>());
            for (ImageConfiguration image: images) {
                for (String container: access.getContainersForImage(image.getName())) {
                    getLog().debug("adding shutdown action for image " + image.getName());
                    actions.add(new ShutdownAction(image.getName(), container));
                }
            }
            getPluginContext().put(DOCKER_SHUTDOWN_ACTIONS, actions);

            List<ShutdownAction> appliedShutdownActions = new ArrayList<>();
            for (ShutdownAction action : getShutdownActionsInExecutionOrder()) {
                action.shutdown(access, this, keepContainer);
                appliedShutdownActions.add(action);
            }
            removeShutdownActions(appliedShutdownActions);
        }
    }
}
