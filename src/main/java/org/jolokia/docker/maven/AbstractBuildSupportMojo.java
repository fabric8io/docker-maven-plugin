package org.jolokia.docker.maven;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenReaderFilter;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.assembly.DockerAssemblyManager;
import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.util.ImageName;
import org.jolokia.docker.maven.util.MojoParameters;

/**
 * @author roland
 * @since 26/06/15
 */
abstract public class AbstractBuildSupportMojo extends AbstractDockerMojo {
    // ==============================================================================================================
    // Parameters required from Maven when building an assembly. They cannot be injected directly
    // into DockerAssemblyCreator.
    // See also here: http://maven.40175.n5.nabble.com/Mojo-Java-1-5-Component-MavenProject-returns-null-vs-JavaDoc-parameter-expression-quot-project-quot-s-td5733805.html
    /** @parameter */
    private MavenArchiveConfiguration archive;

    /** @component */
    protected MavenSession session;

    /** @component */
    private MavenFileFilter mavenFileFilter;

    /** @component */
    private MavenReaderFilter mavenFilterReader;

   
    /**
     * @parameter default-value="src/main/docker" property="docker.source.dir"
     */
    private String sourceDirectory;

    /**
     * @parameter default-value="target/docker" property="docker.target.dir"
     */
    private String outputDirectory;


    protected MojoParameters createMojoParameters() {
        return new MojoParameters(session, project, archive, mavenFileFilter, mavenFilterReader,
                                  sourceDirectory, outputDirectory);
    }

    protected void buildImage(DockerAccess dockerAccess, ImageConfiguration imageConfig)
            throws DockerAccessException, MojoExecutionException {

        autoPullBaseImage(dockerAccess, imageConfig);

        MojoParameters params = createMojoParameters();
        serviceHub.getBuildService().buildImage(imageConfig, params);
    }

    private void autoPullBaseImage(DockerAccess dockerAccess, ImageConfiguration imageConfig)
            throws DockerAccessException, MojoExecutionException {
        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
        String fromImage = buildConfig.getFrom();
        if (fromImage == null) {
            AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();
            if (assemblyConfig == null || assemblyConfig.getDockerFileDir() == null) {
                fromImage = DockerAssemblyManager.DEFAULT_DATA_BASE_IMAGE;
            }
        }
        if (fromImage != null) {
            checkImageWithAutoPull(dockerAccess, fromImage, new ImageName(fromImage).getRegistry(),true);
        }
    }
}
