package org.jolokia.docker.maven;

import java.util.*;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.jolokia.docker.maven.access.DockerAccess;

/**
 * @author roland
 * @since 26.03.14
 */
@Mojo(name = "stop", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class StopMojo extends AbstractDockerMojo {

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
            List<ShutdownAction> appliedShutdownActions = new ArrayList<>();
            for (ShutdownAction action : getShutdownActions()) {
                if (action.applies(image)) {
                    action.shutdown(access, this, keepContainer, keepData);
                    appliedShutdownActions.add(action);
                }
            }
            removeShutdownActions(appliedShutdownActions);
        }
    }
}
