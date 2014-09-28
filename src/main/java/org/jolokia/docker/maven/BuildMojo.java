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
                createDataImage(buildConfig,dockerAccess);
            }
        }
    }


     protected String createDataImage(BuildImageConfiguration buildConfig, DockerAccess dockerAccess)
             throws MojoExecutionException, MojoFailureException {
         String assemblyDescriptor = buildConfig.getAssemblyDescriptor();
         String assemblyDescriptorRef = buildConfig.getAssemblyDescriptorRef();
         if (assemblyDescriptor != null && assemblyDescriptorRef != null) {
             throw new MojoExecutionException("No assemblyDescriptor or assemblyDescriptorRef has been given");
         }
         if (mergeData) {
             if (image == null) {
                 throw new MojoExecutionException("mergeData requires an image to be set");
             }
             checkImage(dockerAccess,image);
             return buildDataImage(image, dockerAccess);
         } else {
             return buildDataImage(null, dockerAccess);
         }
     }

    protected String buildDataImage(String name, BuildImageConfiguration config, DockerAccess dockerAccess) throws MojoFailureException, MojoExecutionException {
        MojoParameters params =  new MojoParameters(session, project, archive, mavenFileFilter);
        String base = config.getBaseImage() != null ? baseImage : dataBaseImage;
        File dockerArchive = dockerArchiveCreator.create(params, base, dataExportDir, assemblyDescriptor, assemblyDescriptorRef);
        info("Created data image " + name);
        dockerAccess.buildImage(name, dockerArchive);
        return name;
    }

}
