package org.jolokia.docker.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.jolokia.docker.maven.access.AuthConfig;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.config.*;
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

    /** {@inheritDoc} */
    @Override
    public void executeInternal(DockerAccess docker) throws DockerAccessException, MojoExecutionException {
        for (ImageConfiguration imageConfig : getImages()) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            String name = imageConfig.getName();
            if (buildConfig != null) {
                String configuredRegistry = getConfiguredRegistry(imageConfig);
                AuthConfig authConfig = prepareAuthConfig(name, configuredRegistry, true);

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
