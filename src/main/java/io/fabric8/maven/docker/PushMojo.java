package io.fabric8.maven.docker;

import java.io.File;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.assembly.BuildDirs;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
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
        createBuildXContexts(hub);
        hub.getRegistryService().pushImages(createProjectPaths(), getResolvedImages(), retries, getRegistryConfig(pushRegistry), skipTag, getBuildContext());
    }

    /**
     * A buildx push builds from the docker build context in the build directory, which is normally created by
     * <code>docker:build</code> or <code>docker:source</code>. Create it here when it does not exist yet, so that
     * <code>docker:push</code> can be used without <code>docker:build</code>. The build directory depends only on the
     * image name, so an existing context may stem from other settings, e.g. another profile using the same image
     * name. Recreate it when its Dockerfile differs from the one that would be created now.
     */
    private void createBuildXContexts(ServiceHub hub) throws MojoExecutionException {
        for (ImageConfiguration imageConfig : getResolvedImages()) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            if (buildConfig == null || !imageConfig.isBuildX() || buildConfig.skipPush() || buildConfig.getDockerArchive() != null) {
                continue;
            }
            BuildDirs buildDirs = new BuildDirs(createProjectPaths(), imageConfig.getName());
            boolean contextExists = new File(buildDirs.getTemporaryRootDirectory(), "docker-build.tar").exists();
            if (!contextExists || !hub.getArchiveService().isDockerfileUpToDate(imageConfig, createMojoParameters())) {
                hub.getArchiveService().createDockerBuildArchive(imageConfig, createMojoParameters());
            }
        }
    }

    private void executeJibPush(ServiceHub hub) throws MojoExecutionException {
        log.info("Pushing Container image with [[B]]JIB(Java Image Builder)[[B]] mode");
        JibBuildService jibBuildService = new JibBuildService(hub, new MojoParameters(session, project, null, null, null,
                settings, sourceDirectory, outputDirectory, null), log);
        jibBuildService.push(getResolvedImages(), retries, getRegistryConfig(pushRegistry), skipTag);
    }

}
