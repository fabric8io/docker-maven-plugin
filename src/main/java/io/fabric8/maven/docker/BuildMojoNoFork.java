package io.fabric8.maven.docker;

import java.util.Date;
import java.util.List;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
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
 */
@Mojo(name = "build-nofork", defaultPhase = LifecyclePhase.INSTALL)
public class BuildMojoNoFork extends AbstractBuildSupportMojo {

    @Parameter(property = "docker.skip.tag", defaultValue = "false")
    private boolean skipTag;

    @Parameter(property = "docker.skip.build", defaultValue = "false")
    protected boolean skipBuild;

    @Override
    protected void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        if (skipBuild) {
            return;
        }
        for (ImageConfiguration imageConfig : getResolvedImages()) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();

            if (buildConfig != null) {
                if (buildConfig.skip()) {
                    log.info("%s : Skipped building", imageConfig.getDescription());
                } else {
                    buildAndTag(hub, imageConfig);
                }
            }
        }

    }

    protected void buildAndTag(ServiceHub hub, ImageConfiguration imageConfig)
        throws MojoExecutionException, DockerAccessException {
        buildImage(hub, imageConfig);
        if (!skipTag) {
            tagImage(imageConfig.getName(), imageConfig, hub.getDockerAccess());
        }
    }

    // We ignore an already existing date file and always return the current date
    @Override
    protected Date getReferenceDate() throws MojoExecutionException {
        return new Date();
    }


    private void tagImage(String imageName, ImageConfiguration imageConfig, DockerAccess dockerAccess)
        throws DockerAccessException, MojoExecutionException {

        List<String> tags = imageConfig.getBuildConfiguration().getTags();
        if (tags.size() > 0) {
            log.info("%s: Tag with %s", imageConfig.getDescription(), EnvUtil.stringJoin(tags, ","));

            for (String tag : tags) {
                if (tag != null) {
                    dockerAccess.tag(imageName, new ImageName(imageName, tag).getFullName(), true);
                }
            }

            log.debug("Tagging image successful!");
        }
    }

}
