package org.jolokia.docker.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.jolokia.docker.maven.access.DockerAccess;

/**
 * Mojo for building a data image (merged or not
 *
 * @author roland
 * @since 28.07.14
 */
@Mojo(name = "build")
public class BuildMojo extends AbstractDataImageSupportMojo {

    @Override
    protected void executeInternal(DockerAccess dockerAccess) throws MojoExecutionException, MojoFailureException {
        createDataImage(dockerAccess);
    }
}
