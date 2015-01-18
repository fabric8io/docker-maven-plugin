package org.jolokia.docker.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.config.*;

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
    public void executeInternal(DockerAccess docker) throws DockerAccessException, MojoExecutionException {
        for (ImageConfiguration imageConfig : getImages()) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            String name = getImageName(imageConfig.getName());
            if (buildConfig != null) {
                docker.pushImage(name, prepareAuthConfig(name), getRegistry(imageConfig));
            }
        }
    }
}
