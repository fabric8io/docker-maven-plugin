package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.service.ServiceHub;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Goal for pushing a data-docker container
 *
 * @author roland
 */
@Mojo(name = "push", defaultPhase = LifecyclePhase.DEPLOY)
public class PushMojo extends AbstractDockerMojo {

    // Registry to use for push operations if no registry is specified
    @Parameter(property = "docker.push.registry")
    private String pushRegistry;

    @Parameter(property = "docker.skip.push", defaultValue = "false")
    private boolean skipPush;

    @Parameter(property = "docker.push.retries", defaultValue = "0")
    private int retries;

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        if (skipPush) {
            return;
        }

        hub.getRegistryService().pushImages(getResolvedImages(), pushRegistry, retries, getRegistryConfig());
    }
}
