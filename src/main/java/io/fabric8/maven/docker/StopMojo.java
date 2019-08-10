package io.fabric8.maven.docker;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import io.fabric8.maven.docker.util.NamePatternUtil;

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

    @Parameter(property = "docker.stopNamePattern")
    private String stopNamePattern;

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

    private void stopContainers(QueryService queryService, RunService runService, GavLabel gavLabel)
            throws MojoExecutionException, IOException, ExecException {

        Collection<Network> networksToRemove = getNetworksToRemove(queryService, gavLabel);

        for (ImageConfiguration image : getResolvedImages()) {

            Collection<Container> existingContainers
                    = getContainersForImage(queryService, image);

            for (Container container : existingContainers) {
                if (shouldStopContainer(container, gavLabel)) {
                    runService.stopContainer(container.getId(), image, keepContainer, removeVolumes);
                }
            }
        }

        // If the mojo has a stopNamePattern, check to see if there are matching containers
        for (Container container : getContainersForMojo(queryService)) {
            if (shouldStopContainer(container, gavLabel)) {
                runService.stopContainer(container.getId(),
                        new ImageConfiguration.Builder().name(container.getImage()).build(),
                        keepContainer, removeVolumes);
            }
        }

        runService.removeCustomNetworks(networksToRemove);
    }

    private Collection<Container> getContainersForPattern(QueryService queryService, Matcher imageNameMatcher,
                                                          Matcher containerNameMatcher)
            throws IOException {
        return queryService.listContainers(!keepContainer)
                .stream()
                .filter(c -> containerMatchesPattern(c, imageNameMatcher, containerNameMatcher))
                .collect(Collectors.toList());
    }

    private Collection<Container> getContainersForMojo(QueryService queryService)
            throws MojoExecutionException, IOException {
        if(stopNamePattern != null) {
            Matcher imageNameMatcher = getImageNameMatcher(stopNamePattern);

            Matcher containerNameMatcher = getContainerNameMatcher(stopNamePattern);

            if(imageNameMatcher == null && containerNameMatcher == null) {
                log.warn("There are no image name or container name patterns in non-empty stopNamePattern: no containers will be stopped");
            } else {
                return getContainersForPattern(queryService, imageNameMatcher, containerNameMatcher);
            }
        }

        return Collections.emptyList();
    }

    private Collection<Container> getContainersForImage(QueryService queryService, ImageConfiguration image)
            throws MojoExecutionException, IOException {

        if(image.getStopNamePattern() != null) {
            Matcher imageNameMatcher = getImageNameMatcher(image.getStopNamePattern());

            Matcher containerNameMatcher = getContainerNameMatcher(image.getStopNamePattern());

            if(imageNameMatcher == null && containerNameMatcher == null) {
                log.warn("There are no image name or container name patterns in stopNamePattern for image %s: no containers will be stopped", image.getName());
                return Collections.emptyList();
            }

            return getContainersForPattern(queryService, imageNameMatcher, containerNameMatcher);
        }

        return ContainerNamingUtil.getContainersToStop(image,
                containerNamePattern,
                getBuildTimestamp(),
                queryService.getContainersForImage(image.getName(), !keepContainer));
    }

    private Matcher getImageNameMatcher(String stopNamePattern) throws MojoExecutionException {
        try {
            String imageNameRegex = NamePatternUtil.convertNamePatternList(stopNamePattern, NamePatternUtil.IMAGE_FIELD, true);
            if(imageNameRegex == null) {
                log.debug("No image name patterns in stopNamePattern %s", stopNamePattern);
                return null;
            } else {
                log.debug("Converted stopNamePattern %s into image name regular expression %s", stopNamePattern, imageNameRegex);
                return Pattern.compile(imageNameRegex).matcher("");
            }
        } catch(IllegalArgumentException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private Matcher getContainerNameMatcher(String stopNamePattern) throws MojoExecutionException {
        try {
            String containerNameRegex = NamePatternUtil.convertNamePatternList(stopNamePattern, NamePatternUtil.NAME_FIELD, true);;
            if(containerNameRegex == null) {
                log.debug("No container name patterns in stopNamePattern %s", stopNamePattern);
                return null;
            } else {
                log.debug("Converted stopNamePattern %s into container name regular expression %s", stopNamePattern, containerNameRegex);
                return Pattern.compile(containerNameRegex).matcher("");
            }
        } catch(IllegalArgumentException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private boolean containerMatchesPattern(Container container, Matcher imageNameMatcher, Matcher containerNameMatcher) {
        if(imageNameMatcher != null && container.getImage() != null && imageNameMatcher.reset(container.getImage()).find()) {
            log.debug("Container image %s matched stopNamePattern", container.getImage());
            return true;
        } else if(containerNameMatcher != null && container.getName() != null && containerNameMatcher.reset(container.getName()).find()) {
            log.debug("Container name %s matched stopNamePattern", container.getName());
            return true;
        } else {
            log.debug("Neither container image %s nor name %s matched stopNamePattern", container.getImage(), container.getName());
            return false;
        }
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
                                                        queryService.getContainersForImage(image.getName(), !keepContainer));

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
