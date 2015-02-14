package org.jolokia.docker.maven;

import java.io.File;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.assembly.DockerAssemblyManager;
import org.jolokia.docker.maven.config.BuildImageConfiguration;
import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.util.ImageName;
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
    /** @parameter */
    private MavenArchiveConfiguration archive;

    /** @component */
    private MavenSession session;

    /** @component */
    private MavenFileFilter mavenFileFilter;

    /** @component */
    private DockerAssemblyManager dockerAssemblyManager;

    /**
     * @parameter default-value="src/main/docker" property="docker.source.dir"
     */
    private String sourceDirectory;
    
    /**
     * @parameter default-value="target/docker" property="docker.target.dir"
     */
    private String outputDirectory;
    
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
        String imageName = getImageName(imageConfig.getName());
        info("Creating image " + imageConfig.getDescription());

        String fromImage = imageConfig.getBuildConfiguration().getFrom();
        if (fromImage == null) {
            fromImage = DockerAssemblyManager.DEFAULT_DATA_BASE_IMAGE;
        }

        checkImageWithAutoPull(dockerAccess, fromImage, new ImageName(fromImage).getRegistry());

        MojoParameters params =  new MojoParameters(session, project, archive, mavenFileFilter, sourceDirectory, outputDirectory);
        File dockerArchive = dockerAssemblyManager.create(params, imageConfig.getBuildConfiguration());

        dockerAccess.buildImage(imageName, dockerArchive);
        debug("Build successful!");
    }


}
