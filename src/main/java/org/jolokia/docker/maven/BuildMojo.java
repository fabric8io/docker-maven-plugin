package org.jolokia.docker.maven;

import java.io.File;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.assembly.DockerArchiveCreator;
import org.jolokia.docker.maven.config.BuildImageConfiguration;
import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.util.MojoParameters;

/**
 * Mojo for building a data image
 *
 * @author roland
 * @since 28.07.14
 */
@Mojo(name = "build")
public class BuildMojo extends AbstractDockerMojo {

    // ==============================================================================================================
    // Parameters required from Maven when building an assembly. They cannot be injected directly
    // into DockerAssemblyCreator.
    // See also here: http://maven.40175.n5.nabble.com/Mojo-Java-1-5-Component-MavenProject-returns-null-vs-JavaDoc-parameter-expression-quot-project-quot-s-td5733805.html
    @Parameter
    private MavenArchiveConfiguration archive;

    @Component
    private MavenSession session;

    @Component
    private MavenFileFilter mavenFileFilter;

    @Component
    protected MavenProject project;

    @Component
    private DockerArchiveCreator dockerArchiveCreator;

    @Override
    protected void executeInternal(DockerAccess dockerAccess) throws MojoExecutionException, MojoFailureException {
        for (ImageConfiguration imageConfig : images) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            if (buildConfig != null) {
                buildImage(imageConfig.getName(),buildConfig, dockerAccess);
            }
        }
    }

    private void buildImage(String name, BuildImageConfiguration buildConfig, DockerAccess dockerAccess)
            throws MojoExecutionException, MojoFailureException {
        MojoParameters params =  new MojoParameters(session, project, archive, mavenFileFilter);
        File dockerArchive = dockerArchiveCreator.create(params, buildConfig);
        String imageName = getImageName(name);
        info("Created image " + imageName);
        dockerAccess.buildImage(imageName, dockerArchive);
    }
}
