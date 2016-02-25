package io.fabric8.maven.docker;

import java.io.File;
import java.io.IOException;

import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.*;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenReaderFilter;
import io.fabric8.maven.docker.access.DockerAccessException;

/**
 * @author roland
 * @since 26/06/15
 */
abstract public class AbstractBuildSupportMojo extends AbstractDockerMojo {
    // ==============================================================================================================
    // Parameters required from Maven when building an assembly. They cannot be injected directly
    // into DockerAssemblyCreator.
    // See also here: http://maven.40175.n5.nabble.com/Mojo-Java-1-5-Component-MavenProject-returns-null-vs-JavaDoc-parameter-expression-quot-project-quot-s-td5733805.html

    @Parameter
    private MavenArchiveConfiguration archive;

    @Parameter(defaultValue = "${session}", readonly = true)
    protected MavenSession session;

    @Component
    private MavenFileFilter mavenFileFilter;

    @Component
    private MavenReaderFilter mavenFilterReader;

    @Parameter(property = "docker.pull.registry")
    private String pullRegistry;

    @Parameter(property = "docker.source.dir", defaultValue="src/main/docker")
    private String sourceDirectory;

    @Parameter(property = "docker.target.dir", defaultValue="target/docker")
    private String outputDirectory;

    protected MojoParameters createMojoParameters() {
        return new MojoParameters(session, project, archive, mavenFileFilter, mavenFilterReader,
                                  sourceDirectory, outputDirectory);
    }

    protected void buildImage(ServiceHub hub, ImageConfiguration imageConfig)
            throws DockerAccessException, MojoExecutionException {

        autoPullBaseImage(hub, imageConfig);

        MojoParameters params = createMojoParameters();
        hub.getBuildService().buildImage(imageConfig, params, checkForNocache(imageConfig));
    }

    private void autoPullBaseImage(ServiceHub hub, ImageConfiguration imageConfig)
            throws DockerAccessException, MojoExecutionException {
        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
        String fromImage = buildConfig.getFrom();
        if (fromImage == null) {
            AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();
            if (assemblyConfig == null) {
                fromImage = DockerAssemblyManager.DEFAULT_DATA_BASE_IMAGE;
            } else if (assemblyConfig.getDockerFileDir() != null) {
                try {
                    File dockerFileDir = EnvUtil.prepareAbsoluteSourceDirPath(createMojoParameters(),
                                                                              assemblyConfig.getDockerFileDir());
                    fromImage = DockerFileUtil.extractBaseImage(new File(dockerFileDir, "Dockerfile"));
                } catch (IOException e) {
                    // Cant extract base image, so we wont try an autopull. An error will occur later anyway when
                    // building the image, so we are passive here.
                    fromImage = null;
                }
            }
        }
        if (fromImage != null) {
            String pullRegistry = EnvUtil.findRegistry(new ImageName(fromImage).getRegistry(), this.pullRegistry, registry);
            checkImageWithAutoPull(hub, fromImage, pullRegistry, true);
        }
    }

    private boolean checkForNocache(ImageConfiguration imageConfig) {
        String nocache = System.getProperty("docker.nocache");
        if (nocache != null) {
            return nocache.length() == 0 || Boolean.valueOf(nocache);
        } else {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            return buildConfig.nocache();
        }
    }
}
