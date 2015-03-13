package org.jolokia.docker.maven;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.log.LogDispatcher;

/**
 * Mojo for stopping containers. If called together with <code>docker:start</code> (i.e.
 * when configured for integration testing in a lifefcycle phase), then only the container
 * started by this goal will be stopped and removed by default (this can be tuned with the
 * system property <code>docker.keepContainer</code>).
 *
 * If this goal is called standalone, then <em>all</em> containers are stopped, for which images
 * has been configured in the pom.xml
 *
 * @author roland
 * @since 26.03.14
 *
 * @goal stop
 * @phase post-integration-test
 */
public class StopMojo extends AbstractDockerMojo {

    // Whether to keep the containers afters stopping
    /**
     * @parameter property = "docker.keepContainer" default-value = "false"
     */
    private boolean keepContainer;

    // Whether to *not* stop the container. Mostly useful as a command line param
    /**
     * @parameter property = "docker.keepRunning" defaultValue = "false"
     */
    private boolean keepRunning;

    // Whether to remove volumes when removing the container
    /**
     * @parameter property = "docker.removeVolumes" defaultValue = "false"
     */
    private boolean removeVolumes;

    @Override
    protected void executeInternal(DockerAccess access) throws MojoExecutionException, DockerAccessException {

        Boolean startCalled = (Boolean) getPluginContext().get(CONTEXT_KEY_START_CALLED);

        if (!keepRunning) {
            if (startCalled == null || !startCalled) {
                // Called directly ....
                for (ImageConfiguration image : getImages()) {
                    String imageName = image.getName();
                    for (String container : access.getContainersForImage(imageName)) {
                        new ShutdownAction(image, container).shutdown(access, log, keepContainer, removeVolumes);
                    }
                }
            } else {
                // Called from a lifecycle phase ...
                List<ShutdownAction> appliedShutdownActions = new ArrayList<>();
                for (ShutdownAction action : getShutdownActionsInExecutionOrder()) {
                    action.shutdown(access, log, keepContainer, removeVolumes);
                    appliedShutdownActions.add(action);
                }
                removeShutdownActions(appliedShutdownActions);
            }
        }

        // Switch off all logging
        LogDispatcher dispatcher = getLogDispatcher(access);
        dispatcher.untrackAllContainerLogs();
    }
}
