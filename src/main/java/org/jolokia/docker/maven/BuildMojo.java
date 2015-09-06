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
public class BuildMojo extends AbstractBuildSupportMojo {

    /**
     * @parameter default-value="false" property="docker.skipTags"
     */
    private boolean skipTags;

    @Override
    protected void executeInternal(DockerAccess dockerAccess) throws DockerAccessException, MojoExecutionException {
        for (ImageConfiguration imageConfig : getImages()) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            if (buildConfig != null) {
                if (buildConfig.skip()) {
                    log.info(imageConfig.getDescription() + ": Skipped building");
                } else {
                    buildAndTag(dockerAccess, imageConfig);
                }
            }
        }
    }

    private void buildAndTag(DockerAccess dockerAccess, ImageConfiguration imageConfig)
        throws MojoExecutionException, DockerAccessException {
        buildImage(dockerAccess, imageConfig);
        if (!skipTags) {
            tagImage(imageConfig.getName(), imageConfig, dockerAccess);
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
