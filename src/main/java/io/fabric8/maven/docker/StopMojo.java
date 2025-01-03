package io.fabric8.maven.docker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.fabric8.maven.docker.access.DockerAccessException;
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

    private static final String STOP_NAME_PATTERN_CONFIG = "stopNamePattern";

    @Parameter(property = "docker.keepRunning", defaultValue = "false")
    private boolean keepRunning;

    /**
     * Whether to create the customs networks (user-defined bridge networks) before starting automatically
     */
    @Parameter(property = "docker.autoCreateCustomNetworks", defaultValue = "false")
    protected boolean autoCreateCustomNetworks;

    @Parameter( property = "docker.allContainers", defaultValue = "false" )
    boolean allContainers;

    @Parameter( property = "docker.sledgeHammer", defaultValue = "false" )
    private boolean sledgeHammer;

    /**
     * Naming pattern for how to name containers when started
     */
    @Parameter(property = "docker.containerNamePattern")
    String containerNamePattern = ContainerNamingUtil.DEFAULT_CONTAINER_NAME_PATTERN;

    @Parameter(property = "docker.stopNamePattern")
    String stopNamePattern;

    @Parameter(property = "docker.skip", defaultValue = "false")
    protected boolean skip;

    /**
     * If true, the containers are not stopped right away, but when the build is finished (success or failed).
     */
    @Parameter(property = "docker.executeStopOnVMShutdown", defaultValue = "false")
    private boolean executeStopOnVMShutdown;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if(skip) {
            return;
        }

        if (this.executeStopOnVMShutdown) {
            this.executeStopOnVMShutdown = false;
           if (!invokedTogetherWithDockerStart()) {
                throw new MojoExecutionException("docker:stop with executeStopOnVMShutdown is only possible if" +
                        " the docker containers started within the same maven session.");
            }

            try {
                // HttpDelete class is not used during start mojo, so we need to load it and initialize it. It is not
                // possible to load classes in the shutdown hook as
                Class.forName("org.apache.http.client.methods.HttpDelete", true, this.getClass().getClassLoader());
            } catch (ClassNotFoundException e) {
                log.error("Failure in loading org.apache.http.client.methods.HttpDelete class: %s", e.getMessage());
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    super.execute();
                } catch (MojoExecutionException | MojoFailureException e) {
                    log.error("Failure in stopping container: %s", e.getMessage());
                }
            }));
        } else {
            super.execute();
        }
    }

    @Override
    protected void executeInternal(ServiceHub hub) throws MojoExecutionException, IOException, ExecException {
        if(skip) {
            return;
        }

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
		List<DockerAccessException> thrownExceptions = new ArrayList<>();
        for (ImageConfiguration image : getResolvedImages()) {

            Collection<Container> existingContainers
                    = getContainersForImage(queryService, image);

            for (Container container : existingContainers) {
                if (shouldStopContainer(container, gavLabel)) {
					try {
						runService.stopContainer(container.getId(), image, keepContainer, removeVolumes);
					} catch (DockerAccessException exc) {
						thrownExceptions.add(exc);
					}
                }
            }
        }
        // If the mojo has a stopNamePattern, check to see if there are matching containers
        for (Container container : getContainersForMojo(queryService)) {
            if (shouldStopContainer(container, gavLabel)) {
            	try {
	                runService.stopContainer(container.getId(),
	                        new ImageConfiguration.Builder().name(container.getImage()).build(),
	                        keepContainer, removeVolumes);
            	} catch (DockerAccessException exc) {
					thrownExceptions.add(exc);
				}
            }
        }

		try {
			runService.removeCustomNetworks(networksToRemove);
		} catch (DockerAccessException exc) {
			thrownExceptions.add(exc);
		}

		if (!thrownExceptions.isEmpty()) {
			DockerAccessException exception = new DockerAccessException("At least one exception thrown during container removal.");
			for (DockerAccessException dae : thrownExceptions) {
				exception.addSuppressed(dae);
			}
			throw exception;
		}
    }

    private Collection<Container> getContainersForMojo(QueryService queryService)
            throws MojoExecutionException, IOException {
        if(stopNamePattern != null) {
            Matcher imageNameMatcher = getImageNameMatcher(stopNamePattern, STOP_NAME_PATTERN_CONFIG);

            Matcher containerNameMatcher = getContainerNameMatcher(stopNamePattern, STOP_NAME_PATTERN_CONFIG);

            if(imageNameMatcher == null && containerNameMatcher == null) {
                log.warn("There are no image name or container name patterns in non-empty stopNamePattern: no containers will be stopped");
            } else {
                return getContainersForPattern(queryService, !keepContainer, imageNameMatcher, containerNameMatcher, STOP_NAME_PATTERN_CONFIG);
            }
        }

        return Collections.emptyList();
    }

    private Collection<Container> getContainersForImage(QueryService queryService, ImageConfiguration image)
            throws MojoExecutionException, IOException {

        String imageStopNamePattern = image.getStopNamePattern();
        if (imageStopNamePattern != null) {
            Matcher imageNameMatcher = getImageNameMatcher(imageStopNamePattern, STOP_NAME_PATTERN_CONFIG);

            Matcher containerNameMatcher = getContainerNameMatcher(imageStopNamePattern, STOP_NAME_PATTERN_CONFIG);

            if(imageNameMatcher == null && containerNameMatcher == null) {
                log.warn("There are no image name or container name patterns in stopNamePattern for image %s: no containers will be stopped", image.getName());
                return Collections.emptyList();
            }

            return getContainersForPattern(queryService, !keepContainer, imageNameMatcher, containerNameMatcher, STOP_NAME_PATTERN_CONFIG);
        }

        return ContainerNamingUtil.getContainersToStop(image,
                containerNamePattern,
                getBuildTimestamp(),
                queryService.getContainersForImage(image.getName(), !keepContainer));
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
