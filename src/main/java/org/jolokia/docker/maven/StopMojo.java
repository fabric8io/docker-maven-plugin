package org.jolokia.docker.maven;

import java.util.List;
import java.util.Map;

import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.maven.plugin.MojoExecutionException;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.config.RunImageConfiguration;
import org.jolokia.docker.maven.log.LogDispatcher;
import org.jolokia.docker.maven.model.Container;
import org.jolokia.docker.maven.service.*;
import org.jolokia.docker.maven.util.PomLabel;

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
    protected void executeInternal(ServiceHub hub) throws MojoExecutionException, DockerAccessException {
        QueryService queryService = hub.getQueryService();
        RunService runService = hub.getRunService();
        
        PomLabel pomLabel = getPomLabel();

        if (!keepRunning) {
            if (invokedTogetherWithDockerStart()) {
                runService.stopStartedContainers(keepContainer, removeVolumes, pomLabel);
            } else {
                stopContainers(queryService, runService, pomLabel);
            }
        }

        // Switch off all logging
        LogDispatcher dispatcher = getLogDispatcher(hub);
        dispatcher.untrackAllContainerLogs();
    }

    private void stopContainers(QueryService queryService, RunService runService, PomLabel pomLabel) throws DockerAccessException {
        for (ImageConfiguration image : getImages()) {
            for (Container container : getContainersToStop(queryService, image)) {
                if (shouldStopContainer(container, pomLabel)) {
                    runService.stopContainer(container.getId(), image, keepContainer, removeVolumes);
                }
            }
        }
    }

    // If naming strategy is alias stop a container with this name, otherwise get all containers with this image's name
    private List<Container> getContainersToStop(QueryService queryService, ImageConfiguration image) throws DockerAccessException {
        List<Container> containers;
        RunImageConfiguration.NamingStrategy strategy = image.getRunConfiguration().getNamingStrategy();
        if (strategy == RunImageConfiguration.NamingStrategy.alias) {
            Container container = queryService.getContainerByName(image.getAlias());
            containers = container != null ? Collections.singletonList(container) : Collections.emptyList();
        } else {
            containers = queryService.getContainersForImage(image.getName());
        }
        return containers;
    }

    private boolean shouldStopContainer(Container container, PomLabel pomLabel) {
        if (isStopAllContainers()) {
            return true;
        }

        String key = pomLabel.getKey();
        Map<String, String> labels = container.getLabels();

        return labels.containsKey(key) && pomLabel.matches(new PomLabel(labels.get(key)));
    }

    private boolean isStopAllContainers() {
        for (String prop : new String[] { "docker.allContainers", "docker.sledgeHammer" }) {
            String val = System.getProperty(prop);
            if (val != null && Boolean.valueOf(val)) {
                return true;
            }
        }
        return false;
    }

    private boolean invokedTogetherWithDockerStart() {
        Boolean startCalled = (Boolean) getPluginContext().get(CONTEXT_KEY_START_CALLED);
        return startCalled != null && startCalled;
    }
}
