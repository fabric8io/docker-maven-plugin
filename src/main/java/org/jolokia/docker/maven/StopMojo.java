package org.jolokia.docker.maven;

import java.util.ArrayList;
import java.util.List;

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

        Boolean startCalled = (Boolean) getPluginContext().get(CONTEXT_KEY_START_CALLED);

        if (startCalled == null || !startCalled) {
            // Called directly ....

            for (ImageConfiguration image : getImages()) {
                String imageName = image.getName();
                for (String container : access.getContainersForImage(imageName)) {
                    new ShutdownAction(imageName,container).shutdown(access, this, keepContainer);
                }
            }
        } else {
            // Called from a lifecycle phase ...
            if (!keepRunning) {
                List<ShutdownAction> appliedShutdownActions = new ArrayList<>();
                for (ShutdownAction action : getShutdownActionsInExecutionOrder()) {
                    action.shutdown(access, this, keepContainer);
                    appliedShutdownActions.add(action);
                }
                removeShutdownActions(appliedShutdownActions);
            }
        }
    }
}
