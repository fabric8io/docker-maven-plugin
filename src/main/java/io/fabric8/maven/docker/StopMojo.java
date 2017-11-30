package io.fabric8.maven.docker;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.maven.docker.access.ExecException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.NetworkConfig;
import io.fabric8.maven.docker.log.LogDispatcher;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.model.Network;
import io.fabric8.maven.docker.service.QueryService;
import io.fabric8.maven.docker.service.RunService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.ContainerNamingUtil;
import io.fabric8.maven.docker.util.GavLabel;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

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

    @Parameter( property = "docker.allContainers", defaultValue = "false" )
    private boolean allContainers;

    @Parameter( property = "docker.sledgeHammer", defaultValue = "false" )
    private boolean sledgeHammer;

    /**
     * Naming pattern for how to name containers when started
     */
    @Parameter(property = "docker.containerNamePattern")
    private String containerNamePattern = ContainerNamingUtil.DEFAULT_CONTAINER_NAME_PATTERN;

    @Parameter( property = "docker.stopUsingRegex", defaultValue = "false" )
    private boolean userRegexForStopping;

    @Override
    protected void executeInternal(ServiceHub hub) throws MojoExecutionException, IOException, ExecException {
        QueryService queryService = hub.getQueryService();
        RunService runService = hub.getRunService();

        GavLabel gavLabel = getGavLabel();

        if (!keepRunning) {
            if (invokedTogetherWithDockerStart()) {
                runService.stopStartedContainers(keepContainer, removeVolumes, autoCreateCustomNetworks, gavLabel);
            } else {
                stopContainers(queryService, runService, gavLabel);
            }
        }

        // Switch off all logging
        LogDispatcher dispatcher = getLogDispatcher(hub);
        dispatcher.untrackAllContainerLogs();
    }

    private void stopContainers(QueryService queryService, RunService runService, GavLabel gavLabel) throws IOException, ExecException {
        Collection<Network> networksToRemove = getNetworksToRemove(queryService, gavLabel);
        for (ImageConfiguration image : getResolvedImages()) {

            Collection<Container> existingContainers =
                ContainerNamingUtil.getContainersToStop(image,
                                                        containerNamePattern,
                                                        getBuildTimestamp(),
                                                        queryService.getContainersForImage(image.getName(), false));
            for (Container container : existingContainers) {
                if (shouldStopContainer(container, gavLabel)) {
                    runService.stopContainer(container.getId(), image, keepContainer, removeVolumes);
                }
            }
        }
        runService.removeCustomNetworks(networksToRemove);
    }

    // If naming strategy is alias stop a container with this name, otherwise get all containers with this image's name
    private List<Container> getContainersToStop(QueryService queryService, ImageConfiguration image) throws DockerAccessException {
        RunImageConfiguration.NamingStrategy strategy = image.getRunConfiguration().getNamingStrategy();

        if (strategy == RunImageConfiguration.NamingStrategy.alias) {
            if(userRegexForStopping) {
                return queryService.findContainersByRegex(image.getAliasRegex());
            }
            else {
                Container container = queryService.getContainer(image.getAlias());
                return container != null ? Collections.singletonList(container) : Collections.<Container>emptyList();
            }
        } else {
            if(userRegexForStopping) {
            	List<String> imageNames = queryService.findImageNamesByRegex(image.getNameRegex());
                List<Container> containers = new ArrayList<Container>();
            	for(String imageName : imageNames) {
            		List<Container> foundContainers = queryService.getContainersForImage(imageName);
            		containers.addAll(foundContainers);
            	}
                return containers;
            }
            else {
                return queryService.getContainersForImage(image.getName());
            }
        }
    }

    private boolean shouldStopContainer(Container container, PomLabel pomLabel, ImageConfiguration image) {
        if (isStopAllContainers()) {
            return true;
        }
        String key = gavLabel.getKey();
        Map<String, String> labels = container.getLabels();
        return labels.containsKey(key) && gavLabel.equals(new GavLabel(labels.get(key)));
    }

    private boolean isStopAllContainers() {
        return (allContainers || sledgeHammer);
    }

    private boolean invokedTogetherWithDockerStart() {
        Boolean startCalled = (Boolean) getPluginContext().get(CONTEXT_KEY_START_CALLED);
        return startCalled != null && startCalled;
    }

    private Set<Network> getNetworksToRemove(QueryService queryService, GavLabel gavLabel) throws IOException {
        if (!autoCreateCustomNetworks) {
            return Collections.emptySet();
        }
        Set<Network> customNetworks = new HashSet<>();
        Set<Network> networks = queryService.getNetworks();

        for (ImageConfiguration image : getResolvedImages()) {

            final NetworkConfig config = image.getRunConfiguration().getNetworkingConfig();
            if (!config.isCustomNetwork() || config.getName() == null) {
                continue;
            }
            final Network network = getNetworkByName(networks, config.getCustomNetwork());
            if (network == null) {
                continue;
            }
            customNetworks.add(network);
            Collection<Container> existingContainers =
                ContainerNamingUtil.getContainersToStop(image,
                                                        containerNamePattern,
                                                        getBuildTimestamp(),
                                                        queryService.getContainersForImage(image.getName(), false));

            for (Container container : existingContainers) {
                if (!shouldStopContainer(container, gavLabel)) {
                    // it's sill in use don't collect it
                    customNetworks.remove(network);
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
