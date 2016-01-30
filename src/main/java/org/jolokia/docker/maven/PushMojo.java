package org.jolokia.docker.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.jolokia.docker.maven.access.AuthConfig;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.service.ServiceHub;
import org.jolokia.docker.maven.util.ImageName;

/**
 * Goal for pushing a data-docker container
 *
 * @author roland
 *
 * @goal push
 * @phase deploy
 */
public class PushMojo extends AbstractDockerMojo {

    // Registry to use for push operations if no registry is specified
    /** @parameter property = "docker.push.registry" */
    private String pushRegistry;

    /** {@inheritDoc} */
    @Override
    public void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        for (ImageConfiguration imageConfig : getImages()) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            String name = imageConfig.getName();
            if (buildConfig != null) {
                String configuredRegistry = getConfiguredRegistry(imageConfig, pushRegistry);
                AuthConfig authConfig = prepareAuthConfig(new ImageName(name), configuredRegistry, true);

                DockerAccess docker = hub.getDockerAccess();
                docker.pushImage(name, authConfig, configuredRegistry);

                for (String tag : imageConfig.getBuildConfiguration().getTags()) {
                    if (tag != null) {
                        docker.pushImage(new ImageName(name,tag).getFullName(), authConfig, configuredRegistry);
                    }
                }
            }
        }
    }
}
