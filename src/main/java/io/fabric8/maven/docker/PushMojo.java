package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.service.JibBuildService;
import io.fabric8.maven.docker.service.ServiceHub;

import io.fabric8.maven.docker.util.MojoParameters;
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
    boolean skipPush;
    
    /** 
     * Skip building tags
     */
    @Parameter(property = "docker.skip.tag", defaultValue = "false")
    private boolean skipTag;
    
    @Parameter(property = "docker.push.retries", defaultValue = "0")
    private int retries;

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        if (skipPush || shouldSkipPom()) {
            return;
        }

        if (Boolean.TRUE.equals(jib)) {
            executeJibPush(hub);
        } else {
            executeDockerPush(hub);
        }
    }

    private void executeDockerPush(ServiceHub hub) throws MojoExecutionException, DockerAccessException {
        hub.getRegistryService().pushImages(createProjectPaths(), getResolvedImages(), retries, getRegistryConfig(pushRegistry), skipTag, createMojoParameters());
    }

    private void executeJibPush(ServiceHub hub) throws MojoExecutionException {
        log.info("Pushing Container image with [[B]]JIB(Java Image Builder)[[B]] mode");
        JibBuildService jibBuildService = new JibBuildService(hub, new MojoParameters(session, project, null, null, null,
                settings, sourceDirectory, outputDirectory, null), log);
        jibBuildService.push(getResolvedImages(), retries, getRegistryConfig(pushRegistry), skipTag);
    }

}
