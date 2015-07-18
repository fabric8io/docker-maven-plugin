package org.jolokia.docker.maven;

import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.config.BuildImageConfiguration;
import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.util.EnvUtil;
import org.jolokia.docker.maven.util.ImageName;

/**
 * Mojo for building a data image
 *
 * @author roland
 * @since 28.07.14
 *
 * @goal build
 * @phase install
 */
public class BuildMojo extends AbstractBuildSupporMojo {

    /**
     * @parameter default-value="false" property="docker.skipTags"
     */
    private boolean skipTags;

    @Override
    protected void executeInternal(DockerAccess dockerAccess) throws DockerAccessException, MojoExecutionException {
        for (ImageConfiguration imageConfig : getImages()) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            if (buildConfig != null) {
                if (imageConfig.getBuildRunMode().isBuild()) {
                    buildImage(dockerAccess, imageConfig, buildConfig);
                } else {
                    log.info(imageConfig.getDescription() + ": Skipped, 'build' mode not enabled");
                }
            }
        }
    }

    private void buildImage(DockerAccess dockerAccess, ImageConfiguration imageConfig, BuildImageConfiguration buildConfig)
        throws MojoExecutionException, DockerAccessException {
        buildConfig.validate();
        String imageName = imageConfig.getName();
        buildImage(dockerAccess, imageName, imageConfig);
        if (!skipTags) {
            tagImage(imageName, imageConfig, dockerAccess);
        }
    }
    
    private void tagImage(String imageName, ImageConfiguration imageConfig, DockerAccess dockerAccess)
            throws DockerAccessException, MojoExecutionException {

        List<String> tags = imageConfig.getBuildConfiguration().getTags();
        if (tags.size() > 0) {
            log.info(imageConfig.getDescription() + ": Tag with " + EnvUtil.stringJoin(tags, ","));

            for (String tag : tags) {
                if (tag != null) {
                    dockerAccess.tag(imageName, new ImageName(imageName, tag).getFullName(), true);
                }
            }

            log.debug("Tagging image successful!");
        }
    }

}
