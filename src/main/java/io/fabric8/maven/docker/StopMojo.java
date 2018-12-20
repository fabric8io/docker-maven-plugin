package io.fabric8.maven.docker;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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

    @Parameter
    private boolean keepRunning;

    /**
     * Whether to create the customs networks (user-defined bridge networks) before starting automatically
     */
    @Parameter
    protected boolean autoCreateCustomNetworks;

    @Parameter
    private boolean allContainers;

    @Parameter
    private boolean sledgeHammer;

    /**
     * Naming pattern for how to name containers when started
     */
    @Parameter
    private String containerNamePattern = ContainerNamingUtil.DEFAULT_CONTAINER_NAME_PATTERN;

    @Override
    protected void executeInternal(ServiceHub hub) throws MojoExecutionException, IOException, ExecException {
        QueryService queryService = hub.getQueryService();
        RunService runService = hub.getRunService();

        GavLabel gavLabel = getGavLabel();

        if (!getKeepRunning()) {
            if (invokedTogetherWithDockerStart()) {
                runService.stopStartedContainers(getKeepContainer(), getRemoveVolumes(), getAutoCreateCustomNetworks(), gavLabel);
            } else {
                stopContainers(queryService, runService, gavLabel);
            }
        }

        // Switch off all logging
        LogDispatcher dispatcher = getLogDispatcher(hub);
        dispatcher.untrackAllContainerLogs();
    }

    @Override
    public String getPrefix() {
        return "docker.";
    }

    private void stopContainers(QueryService queryService, RunService runService, GavLabel gavLabel) throws IOException, ExecException {
        Collection<Network> networksToRemove = getNetworksToRemove(queryService, gavLabel);
        for (ImageConfiguration image : getResolvedImages()) {

            Collection<Container> existingContainers =
                ContainerNamingUtil.getContainersToStop(image,
                                                        getContainerNamePattern(),
                                                        getBuildTimestamp(),
                                                        queryService.getContainersForImage(image.getName(), false));
            for (Container container : existingContainers) {
                if (shouldStopContainer(container, gavLabel)) {
                    runService.stopContainer(container.getId(), image, getKeepContainer(), getRemoveVolumes());
                }
            }
        }
        runService.removeCustomNetworks(networksToRemove);
    }

    private boolean shouldStopContainer(Container container, GavLabel gavLabel) {
        if (isStopAllContainers()) {
            return true;
        }
        String key = gavLabel.getKey();
        Map<String, String> labels = container.getLabels();
        return labels.containsKey(key) && gavLabel.equals(new GavLabel(labels.get(key)));
    }

    private boolean isStopAllContainers() {
        return (getAllContainers() || getSledgeHammer());
    }

    private boolean invokedTogetherWithDockerStart() {
        Boolean startCalled = (Boolean) getPluginContext().get(CONTEXT_KEY_START_CALLED);
        return startCalled != null && startCalled;
    }

    private Set<Network> getNetworksToRemove(QueryService queryService, GavLabel gavLabel) throws IOException {
        if (!getAutoCreateCustomNetworks()) {
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
                                                        getContainerNamePattern(),
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

    private boolean getKeepRunning() {
        return Boolean.parseBoolean(getProperty("keepRunning", "false"));
    }

    private boolean getAutoCreateCustomNetworks() {
        return Boolean.parseBoolean(getProperty("autoCreateCustomNetworks", "false"));
    }

    private boolean getAllContainers() {
        return Boolean.parseBoolean(getProperty("allContainers", "false"));
    }

    private boolean getSledgeHammer() {
        return Boolean.parseBoolean(getProperty("sledgeHammer", "false"));
    }

    private String getContainerNamePattern() {
        return getProperty("containerNamePattern", ContainerNamingUtil.DEFAULT_CONTAINER_NAME_PATTERN);
    }
}
