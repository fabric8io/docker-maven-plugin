package org.jolokia.docker.maven;

import java.io.File;
import java.util.List;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenReaderFilter;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.assembly.DockerAssemblyManager;
import org.jolokia.docker.maven.config.BuildImageConfiguration;
import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.util.*;

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
    private MavenReaderFilter mavenFilterReader;
    
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
                String imageName = imageConfig.getName();
                buildImage(imageName, imageConfig, dockerAccess);
                tagImage(imageName, imageConfig, dockerAccess);
            }
        }
    }

    private void buildImage(String imageName, ImageConfiguration imageConfig, DockerAccess dockerAccess)
            throws DockerAccessException, MojoExecutionException {
        log.info("Creating image " + imageConfig.getDescription());

        String fromImage = imageConfig.getBuildConfiguration().getFrom();
        if (fromImage == null) {
            fromImage = DockerAssemblyManager.DEFAULT_DATA_BASE_IMAGE;
        }

        checkImageWithAutoPull(dockerAccess, fromImage, new ImageName(fromImage).getRegistry());

        MojoParameters params = createMojoParameters();
        File dockerArchive = dockerAssemblyManager.createDockerTarArchive(imageName,params, imageConfig.getBuildConfiguration());

        dockerAccess.buildImage(imageName, dockerArchive);
        log.debug("Build successful!");
    }

    private void tagImage(String imageName, ImageConfiguration imageConfig, DockerAccess dockerAccess)
            throws DockerAccessException, MojoExecutionException {
        List<String> tags = imageConfig.getBuildConfiguration().getTags();
        if (tags.size() > 0) {
            log.info("Tagging image " + imageConfig.getDescription() + ": " + EnvUtil.stringJoin(tags, ","));

            for (String tag : tags) {
                if (tag != null) {
                    dockerAccess.tag(imageName, new ImageName(imageName, tag).getFullName(), true);
                }
            }

            log.debug("Tagging image successful!");
        }
    }

    private MojoParameters createMojoParameters() {
        return new MojoParameters(session, project, archive, mavenFileFilter, mavenFilterReader,
                sourceDirectory, outputDirectory);
    }
}
