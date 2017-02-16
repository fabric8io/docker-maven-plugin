package io.fabric8.maven.docker;

import java.util.Date;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.EnvUtil;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Mojo for building a data image
 *
 * @author roland
 * @since 28.07.14
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.INSTALL)
public class BuildMojo extends AbstractBuildSupportMojo {

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

        EnvUtil.storeTimestamp(getBuildTimestampFile(), getBuildTimestamp());

        BuildService.BuildContext buildContext = getBuildContext();
        BuildService buildService = hub.getBuildService();

        buildService.buildImage(imageConfig, buildContext);
        if (!skipTag) {
            buildService.tagImage(imageConfig.getName(), imageConfig);
        }
    }

    // We ignore an already existing date file and always return the current date
    @Override
    protected Date getReferenceDate() throws MojoExecutionException {
        return new Date();
    }
}
