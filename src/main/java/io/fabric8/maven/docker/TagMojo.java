package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.List;

/**
 * Goal for Tagging an image so that it becomes part of a repository.
 *
 */
@Mojo(name = "tag", defaultPhase = LifecyclePhase.INSTALL)
public class TagMojo extends AbstractDockerMojo {
    @Parameter(property = "docker.skip.tag", defaultValue = "false")
    private boolean skipTag;

    @Parameter(property = "docker.image.tag")
    private String tagName;

    @Parameter(property = "docker.image.repo")
    private String repo;

    @Override
    public void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        if (skipTag) {
            return;
        }

        List<ImageConfiguration> imageConfigs = getResolvedImages();
        for (ImageConfiguration imageConfig : imageConfigs) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            if (buildConfig.skipTag()) {
                continue;
            }
            if (buildConfig.isBuildX()) {
                // Tag happens at the building stage.
                continue;
            }

            hub.getBuildService().tagImage(imageConfig.getName(), tagName, repo, buildConfig.cleanupMode());
        }
    }
}
