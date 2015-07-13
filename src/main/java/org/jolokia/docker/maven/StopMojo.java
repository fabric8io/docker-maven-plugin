package org.jolokia.docker.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.log.LogDispatcher;
import org.jolokia.docker.maven.model.Container;
import org.jolokia.docker.maven.service.QueryService;
import org.jolokia.docker.maven.service.RunService;

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

    /**
     * Whether to *not* stop the container. Mostly useful as a command line param.
     *
     * @parameter property = "docker.keepRunning" defaultValue = "false"
     */
    private boolean keepRunning;

    @Override
    protected void executeInternal(DockerAccess access) throws MojoExecutionException, DockerAccessException {
        Boolean startCalled = (Boolean) getPluginContext().get(CONTEXT_KEY_START_CALLED);

        QueryService queryService = serviceFactory.getQueryService(access, log);
        RunService runService = serviceFactory.getRunService(access, log);

        
        if (!keepRunning) {
            if (startCalled == null || !startCalled) {
                // Called directly ....
                for (ImageConfiguration image : getImages()) {
                    String imageName = image.getName();
                    for (Container container : queryService.getContainersForImage(imageName)) {
                        runService.stopContainer(image, container.getId(), keepContainer, removeVolumes);
                    }
                }
            } else {
                runService.stopStartedContainers(keepContainer, removeVolumes);
            }
        }

        // Switch off all logging
        LogDispatcher dispatcher = getLogDispatcher(access);
        dispatcher.untrackAllContainerLogs();
    }
}
