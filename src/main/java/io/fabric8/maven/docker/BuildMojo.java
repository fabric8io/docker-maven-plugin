package io.fabric8.maven.docker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.ImageNameFormatter;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.EnvUtil;

/**
 * Mojo for building a data image
 *
 * @author roland
 * @since 28.07.14
 *
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.INSTALL)
public class BuildMojo extends AbstractBuildSupportMojo {

    @Parameter(property="docker.skipTags", defaultValue="false")
    private boolean skipTags;

    @Override
    protected void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        for (ImageConfiguration imageConfig : getResolvedImages()) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            if (buildConfig != null) {
                if (buildConfig.skip()) {
                    log.info("%s : Skipped building",imageConfig.getDescription());
                } else {
                    buildAndTag(hub, imageConfig);
                }
            }
        }

        // lets store the tag we built in case we need it later on (e.g for the watch goal)
        String tag = project.getProperties().getProperty(ImageNameFormatter.DOCKER_IMAGE_TAG);
        if (tag != null && tag.length() > 0) {
            // lets write the last docker image tag as a text file
            File file = getDockerLabelFile();
            try {
                IOUtils.write(tag, new FileOutputStream(file));
            } catch (IOException e) {
                getLog().error("Failed to write to file: " + file + ". " + e, e);
            }
        }
    }

    private void buildAndTag(ServiceHub hub, ImageConfiguration imageConfig)
        throws MojoExecutionException, DockerAccessException {
        buildImage(hub, imageConfig);
        if (!skipTags) {
            tagImage(imageConfig.getName(), imageConfig, hub.getDockerAccess());
        }
    }
    
    private void tagImage(String imageName, ImageConfiguration imageConfig, DockerAccess dockerAccess)
            throws DockerAccessException, MojoExecutionException {

        List<String> tags = imageConfig.getBuildConfiguration().getTags();
        if (tags.size() > 0) {
            log.info("%s: Tag with %s",imageConfig.getDescription(),EnvUtil.stringJoin(tags, ","));

            for (String tag : tags) {
                if (tag != null) {
                    dockerAccess.tag(imageName, new ImageName(imageName, tag).getFullName(), true);
                }
            }

            log.debug("Tagging image successful!");
        }
    }

}
