package org.jolokia.docker.maven;

import java.io.File;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.assembly.DockerArchiveCreator;
import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.util.MojoParameters;

/**
 * Mojo for building a data image
 *
 * @author roland
 * @since 28.07.14
 *
 * @goal build
 * @phase install
 */
public class BuildMojo extends AbstractDockerMojo {

    // ==============================================================================================================
    // Parameters required from Maven when building an assembly. They cannot be injected directly
    // into DockerAssemblyCreator.
    // See also here: http://maven.40175.n5.nabble.com/Mojo-Java-1-5-Component-MavenProject-returns-null-vs-JavaDoc-parameter-expression-quot-project-quot-s-td5733805.html
    /**
     * @parameter
     */
    private MavenArchiveConfiguration archive;

    /**
     * @component
     */
    private MavenSession session;

    /**
     * @component
     */
    private MavenFileFilter mavenFileFilter;

    /**
     * @component
     */
    private DockerArchiveCreator dockerArchiveCreator;
    /** @parameter property = "docker.showLogs" default-value="false" */
    private boolean showLogs;

    @Override
    protected void executeInternal(DockerAccess dockerAccess) throws DockerAccessException, MojoExecutionException {
        for (ImageConfiguration imageConfig : getImages()) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            if (buildConfig != null) {
                buildImage(imageConfig, dockerAccess);
            }
        }
    }

    private void buildImage(ImageConfiguration imageConfig, DockerAccess dockerAccess)
            throws DockerAccessException, MojoExecutionException {
        String fromName = imageConfig.getBuildConfiguration().getFrom();
        info("Pulling base image " + fromName);
        dockerAccess.pullImage(fromName, prepareAuthConfig(fromName));

        MojoParameters params =  new MojoParameters(session, project, archive, mavenFileFilter);
        File dockerArchive = dockerArchiveCreator.create(params, imageConfig.getBuildConfiguration());
        String imageName = getImageName(imageConfig.getName());
        info("Creating image " + imageConfig.getDescription());
        dockerAccess.buildImage(imageName, dockerArchive);
    }

    protected boolean showLog(ImageConfiguration imageConfig) {
        if (showLogs) {
            return true;
        } else {
            RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
            if (runConfig != null) {
                LogConfiguration logConfig = runConfig.getLog();
                if (logConfig != null) {
                    return logConfig.isEnabled();
                }
            }
            return false;
        }
    }
}
