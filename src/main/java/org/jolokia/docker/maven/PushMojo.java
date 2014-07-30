package org.jolokia.docker.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jolokia.docker.maven.access.DockerAccess;

/**
 * Goal for pushing a data-docker container
 *
 * @author roland
 */
@Mojo(name = "push")
public class PushMojo extends AbstractDataImageSupportMojo {

    @Parameter(property = "docker.keepData", defaultValue = "true")
    private boolean keepData;

    /** {@inheritDoc} */
    public void executeInternal(DockerAccess docker) throws MojoExecutionException, MojoFailureException {
        String dataImage = createDataImage(docker);
        docker.pushImage(dataImage,prepareAuthConfig(dataImage));
        if (!keepData) {
            docker.removeImage(dataImage);
        }
    }
}
