package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.ExecException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.NamingStrategy;
import io.fabric8.maven.docker.config.NetworkConfig;
import io.fabric8.maven.docker.log.LogDispatcher;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.model.Network;
import io.fabric8.maven.docker.service.QueryService;
import io.fabric8.maven.docker.service.RunService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.ContainerName;
import io.fabric8.maven.docker.util.PomLabel;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 */
@Mojo(name = "stop", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class StopMojo extends AbstractDockerMojo {

    @Parameter(property = "docker.keepRunning", defaultValue = "false")
    private boolean keepRunning;

    /**
     * Whether to create the customs networks (user-defined bridge networks) before starting automatically
     */
    @Parameter(property = "docker.autoCreateCustomNetworks", defaultValue = "false")
    protected boolean autoCreateCustomNetworks;

    /**
     * Default value for the naming strategy for containers.
     */
    @Parameter(property = "docker.namingStrategy", defaultValue = "auto")
    protected NamingStrategy namingStrategy;

    /**
     * Container prefix for the naming strategy auto, the final container name will be
     * &lt;prefix&gt;_&lt;image name&gt;
     */
    @Parameter(property = "docker.containerPrefix", defaultValue = "${project.name}")
    protected String containerPrefix;

    @Parameter( property = "docker.allContainers", defaultValue = "false" )
    private boolean allContainers;

    @Parameter( property = "docker.sledgeHammer", defaultValue = "false" )
    private boolean sledgeHammer;

    @Override
    protected void executeInternal(ServiceHub hub) throws MojoExecutionException, DockerAccessException, ExecException {
        QueryService queryService = hub.getQueryService();
        RunService runService = hub.getRunService();

        PomLabel pomLabel = getPomLabel();

        if (!keepRunning) {
            if (invokedTogetherWithDockerStart()) {
                runService.stopStartedContainers(keepContainer, removeVolumes, autoCreateCustomNetworks, pomLabel);
            } else {
                stopContainers(queryService, runService, pomLabel);
            }
        }

        // Switch off all logging
        LogDispatcher dispatcher = getLogDispatcher(hub);
        dispatcher.untrackAllContainerLogs();
    }

    private void stopContainers(QueryService queryService, RunService runService, PomLabel pomLabel) throws DockerAccessException, ExecException {
        Collection<Network> networksToRemove = getNetworksToRemove(queryService, pomLabel);
        for (ImageConfiguration image : getResolvedImages()) {
            for (Container container : getContainersToStop(queryService, image)) {
                if (shouldStopContainer(container, pomLabel, image)) {
                    runService.stopContainer(container.getId(), image, keepContainer, removeVolumes);
                }
            }
        }
        runService.removeCustomNetworks(networksToRemove);
    }

    // If naming strategy is alias stop a container with this name, otherwise get all containers with this image's name
    private List<Container> getContainersToStop(QueryService queryService, ImageConfiguration image) throws DockerAccessException {
        NamingStrategy strategy = image.getRunConfiguration().getNamingStrategy();

        if (!NamingStrategy.none.equals(strategy)) {
            Container container = queryService.getContainer(ContainerName.calculate(image.getAlias(), strategy, namingStrategy, containerPrefix, image.getName()));
            return container != null ? Collections.singletonList(container) : Collections.<Container>emptyList();
        } else {
            return queryService.getContainersForImage(image.getName());
        }
    }

    private boolean shouldStopContainer(Container container, PomLabel pomLabel, ImageConfiguration image) {
        if (isStopAllContainers()) {
            return true;
        }

        final NamingStrategy strategy = image.getRunConfiguration().getNamingStrategy();
        if (!NamingStrategy.none.equals(strategy)) {
            final String name = ContainerName.calculate(image.getAlias(), strategy, namingStrategy, containerPrefix, image.getName());
            return container.getName().equals(name);
        }

        String key = pomLabel.getKey();
        Map<String, String> labels = container.getLabels();
        return labels.containsKey(key) && pomLabel.equals(new PomLabel(labels.get(key)));
    }

    private boolean isStopAllContainers() {
        return (allContainers || sledgeHammer);
    }

    private boolean invokedTogetherWithDockerStart() {
        Boolean startCalled = (Boolean) getPluginContext().get(CONTEXT_KEY_START_CALLED);
        return startCalled != null && startCalled;
    }

    private Set<Network> getNetworksToRemove(QueryService queryService, PomLabel pomLabel) throws DockerAccessException {
        if (!autoCreateCustomNetworks) {
            return Collections.emptySet();
        }
        Set<Network> customNetworks = new HashSet<>();
        Set<Network> networks = queryService.getNetworks();

        for (ImageConfiguration image : getResolvedImages()) {
            final NetworkConfig config = image.getRunConfiguration().getNetworkingConfig();
            if (config.isCustomNetwork()) {
                Network network = getNetworkByName(networks, config.getCustomNetwork());
                if (network != null) {
                    customNetworks.add(network);
                    for (Container container : getContainersToStop(queryService, image)) {
                        if (!shouldStopContainer(container, pomLabel, image)) {
                            // it's sill in use don't collect it
                            customNetworks.remove(network);
                        }
                    }
                }
            }
        }
        return customNetworks;
    }
    private Network getNetworkByName(Set<Network> networks, String networkName) {
        for (Network network : networks) {
            if (networkName.equals(network.getName())) {
                return network;
            }
        }
        return null;
    }
}
