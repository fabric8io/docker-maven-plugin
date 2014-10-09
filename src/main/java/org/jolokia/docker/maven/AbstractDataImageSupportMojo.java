package org.jolokia.docker.maven;

import java.io.File;
import java.util.Map;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.assembly.DockerArchiveCreator;
import org.jolokia.docker.maven.util.MojoParameters;

/**
 * Base class which supports on-the-fly creation of data container
 *
 * @author roland
 * @since 12.06.14
 */
public abstract class AbstractDataImageSupportMojo extends AbstractDockerMojo {
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

    // Whether to merge the data in the original image or use a separate data image
    @Parameter(property = "docker.mergeData", required = false, defaultValue = "false")
    protected boolean mergeData;


    @Parameter()
    protected Map<String,String> env = null;

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
    // Whether the data image should be kept if an assembly is used

    /**
     * Build a docker image with the given name from the assembly descriptors configured.
     *
     * @param baseImage a base image to use. If null, check the property "dataBaseImage" and then finally use the default
     * @param dockerAccess access to docker
     * @throws MojoFailureException
     * @throws MojoExecutionException
     */
    protected String buildDataImage(String baseImage, DockerAccess dockerAccess) throws MojoFailureException, MojoExecutionException {
        String dataImageName = getDataImageName();
        MojoParameters params =  new MojoParameters(session, project, archive, mavenFileFilter);
        String base = baseImage != null ? baseImage : dataBaseImage;
        File dockerArchive = dockerArchiveCreator.create(params, base, dataExportDir, assemblyDescriptor, assemblyDescriptorRef, env);
        info("Created data image " + dataImageName);
        dockerAccess.buildImage(dataImageName, dockerArchive);
        return dataImageName;
    }

    /**
     * Create a data image according to the configuration: If "mergeData" is true, the data is merged into the configured
     * image (by using it as a base image), otherwise either the configured data base image is used or the default
     * ("busybox:latest")
     *
     * @param dockerAccess docker access object
     * @return name of the create image
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    protected String createDataImage(DockerAccess dockerAccess) throws MojoExecutionException, MojoFailureException {
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
    protected String getDataImageName() {
        String name = dataImage != null ?
                dataImage :
                sanitizeDockerRepo(project.getGroupId()) + "/" + project.getArtifactId() + ":" + project.getVersion();
        return registry != null ? registry + "/" + name : name;
    }

    // Repo names with '.' are considered to be remote registries
    private String sanitizeDockerRepo(String groupId) {
        return groupId.replace('.','-');
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
