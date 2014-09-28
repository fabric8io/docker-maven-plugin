package org.jolokia.docker.maven;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.assembly.DockerArchiveCreator;
import org.jolokia.docker.maven.config.BuildImageConfiguration;
import org.jolokia.docker.maven.util.AuthConfig;
import org.jolokia.docker.maven.util.MojoParameters;

/**
 * Base class which supports on-the-fly creation of data container
 *
 * @author roland
 * @since 12.06.14
 */
public abstract class DataImageBuilder {

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

    /**
     * Build a docker image with the given name from the assembly descriptors configured.
     *
     * @param config build configuration
     * @param dockerAccess access to docker
     * @throws MojoFailureException
     * @throws MojoExecutionException
     */
    protected String buildDataImage(String name, BuildImageConfiguration config, DockerAccess dockerAccess) throws MojoFailureException, MojoExecutionException {
        MojoParameters params =  new MojoParameters(session, project, archive, mavenFileFilter);
        String base = config.getBaseImage() != null ? baseImage : dataBaseImage;
        File dockerArchive = dockerArchiveCreator.create(params, base, dataExportDir, assemblyDescriptor, assemblyDescriptorRef, ports, env);
        info("Created data image " + name);
        dockerAccess.buildImage(name, dockerArchive);
        return name;
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
        if (dataImage != null) {
            return dataImage;
        } else {
            String name = getDefaultUserName() + "/" + getDefaultRepoName() + ":" + project.getVersion();
            return registry != null ? registry + "/" + name : name;
        }
    }

    private String getDefaultRepoName() {
        String repoName = project.getBuild().getFinalName();
        if (repoName == null || repoName.length() == 0) {
            repoName = project.getArtifactId();
        }
        return repoName;
    }


    // Repo names with '.' are considered to be remote registries
    private String getDefaultUserName() {
        String groupId = project.getGroupId();
        String repo = groupId.replace('.','_').replace('-','_');
        return repo.length() > 30 ? repo.substring(0,30) : repo;
    }

}
