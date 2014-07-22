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

    // Name of the data image. By default it is created as "group/artifact:version"
    @Parameter(property = "docker.dataImage", required = false)
    private String dataImage;

    // Base Image name of the data image to use.
    @Parameter(property = "docker.dataBaseImage", required = false, defaultValue = "busybox")
    private String dataBaseImage;

    // Directory as it is exported
    @Parameter(property = "docker.dataExportDir", required = false, defaultValue = "/maven")
    private String dataExportDir;

    // Registry for data image
    @Parameter(property = "docker.registry")
    protected String registry;

    // Whether to pull an image if not yet locally available (not implemented yet)
    @Parameter(property = "docker.autoPull", defaultValue = "true")
    private boolean autoPull;

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
     * @param baseImage a base image to use. If null, check the property "dataBaseImage"
     * @param dockerAccess access to docker
     * @throws MojoFailureException
     * @throws MojoExecutionException
     */
    protected String createDataImage(String baseImage, DockerAccess dockerAccess) throws MojoFailureException, MojoExecutionException {
        String dataImage = getDataImage();
        MojoParameters params =  new MojoParameters(session, project, archive, mavenFileFilter);
        String base = baseImage != null ? baseImage : dataBaseImage;
        File dockerArchive = dockerArchiveCreator.create(params, base, dataExportDir, assemblyDescriptor, assemblyDescriptorRef);
        info("Created data image " + dataImage);
        dockerAccess.buildImage(dataImage, dockerArchive);
        return dataImage;
    }

    protected String getDataImage() {
        String name = dataImage != null ?
                dataImage :
                project.getGroupId() + "/" + project.getArtifactId() + ":" + project.getVersion();
        return registry != null ? registry + "/" + name : name;
    }

    protected void checkImage(DockerAccess docker,String image) throws MojoExecutionException, MojoFailureException {
        if (!docker.hasImage(image)) {
            if (autoPull) {
                docker.pullImage(image,prepareAuthConfig(image));
            } else {
                throw new MojoExecutionException(this, "No image '" + image + "' found",
                                                 "Please enable 'autoPull' or pull image '" + image +
                                                 "' yourself (docker pull " + image + ")");
            }
        }
    }
}
