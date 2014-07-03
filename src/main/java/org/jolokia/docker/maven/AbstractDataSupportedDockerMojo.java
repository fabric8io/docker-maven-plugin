package org.jolokia.docker.maven;

import java.io.File;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.jolokia.docker.maven.assembly.DockerArchiveCreator;
import org.jolokia.docker.maven.util.MojoParameters;

/**
 * Base class which supports on-the-fly creation of data container
 *
 * @author roland
 * @since 12.06.14
 */
public abstract class AbstractDataSupportedDockerMojo extends AbstractDockerMojo {
    /**
     * A descriptor to use for building the data assembly to be exported
     * in an Docker image
     */
    @Parameter
    protected String assemblyDescriptor;

    /**
     * Reference to an assembly descriptor included.
     */
    @Parameter
    protected String assemblyDescriptorRef;

    // Name of the data image. By default it is created as "group/artefact:version"
    @Parameter(property = "docker.dataImage", required = false)
    private String dataImageName;

    // Base Image name of the data image to use.
    @Parameter(property = "docker.dataBaseImage", required = false, defaultValue = "busybox")
    private String dataBaseImage;

    @Component
    private DockerArchiveCreator dockerArchiveCreator;

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

    /**
     * Create a docker image with the given name from the assembly descriptors configured.
     *
     * @param dockerAccess access to docker
     * @throws MojoFailureException
     * @throws MojoExecutionException
     */
    protected String createDataImage(DockerAccess dockerAccess) throws MojoFailureException, MojoExecutionException {
        String dataImage = getDataImageName();
        MojoParameters params =  new MojoParameters(session, project, archive, mavenFileFilter);
        File dockerArchive = dockerArchiveCreator.create(params, dataBaseImage, assemblyDescriptor, assemblyDescriptorRef);
        info("Created docker archive " + dockerArchive);
        dockerAccess.buildImage(dataImage, dockerArchive);
        return dataImage;
    }

    protected String getDataImageName() {
        return dataImageName != null ?
                dataImageName :
                project.getGroupId() + "/" + project.getArtifactId() + ":" + project.getVersion();
    }
}
