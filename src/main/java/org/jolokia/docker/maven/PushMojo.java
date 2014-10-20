package org.jolokia.docker.maven;

import java.util.*;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.config.BuildImageConfiguration;
import org.jolokia.docker.maven.config.ImageConfiguration;

/**
 * Goal for pushing a data-docker container
 *
 * @author roland
 */
@Mojo(name = "push", defaultPhase = LifecyclePhase.DEPLOY)
public class PushMojo extends AbstractDockerMojo {

    // Comma separated list of images names to push
    @Parameter(property = "docker.push")
    private String push;

    /** {@inheritDoc} */
    public void executeInternal(DockerAccess docker) throws DockerAccessException, MojoExecutionException {
        Set imagesToPush = extractImagesToPush(push);

        for (ImageConfiguration imageConfig : images) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            String name = getImageName(imageConfig.getName());
            if (checkForPush(imagesToPush,name,buildConfig)) {
                docker.pushImage(name,prepareAuthConfig(name));
            }
        }
    }

    private Set<String> extractImagesToPush(String push) {
        return push != null ? new HashSet<>(Arrays.asList(push.split("\\s*,\\s*"))) : null;
    }

    private boolean checkForPush(Set imagesToPush, String name, BuildImageConfiguration buildConfig) {
        if (buildConfig == null) {
            return false;
        }
        if (imagesToPush != null) {
            return imagesToPush.contains(name);
        }
        return !buildConfig.isDoNotPush();
    }
}
