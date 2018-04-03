package io.fabric8.maven.docker;

import java.util.Date;
import java.util.List;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.service.ImagePullManager;
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

    @Parameter(property = "docker.skip.build", defaultValue = "false")
    protected boolean skipBuild;
    
    /** 
     * Skip building tags
     */
    @Parameter(property = "docker.skip.tag", defaultValue = "false")
    protected boolean skipTag;

    @Override
    protected void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        if (skipBuild) {
            return;
        }
        List<ImageConfiguration> resolvedImages = getResolvedImages();

        if(resolvedImages.isEmpty()) {
            // No Configuration found, so build one up dynamically.
            if(imageName == null) {
                /*
                 * Image name defaults to:
                 *     `${project.groupId}/${project.artifactId}:${project.version}`
                 */
                imageName = project.getGroupId() + "/" + project.getArtifactId() + ":" +
                        project.getVersion();
            }
            ImageConfiguration aDefaultImageConfig = ImageConfiguration.getDefaultImageConfiguration(imageName,
                    project.getBasedir().getAbsolutePath());
            processImageConfig(hub, aDefaultImageConfig);
            return;
        } else {
            // Iterate over all the ImageConfigurations and process one by one
            for (ImageConfiguration imageConfig : resolvedImages) {
                processImageConfig(hub, imageConfig);
            }
        }
    }

    protected void buildAndTag(ServiceHub hub, ImageConfiguration imageConfig)
            throws MojoExecutionException, DockerAccessException {

        EnvUtil.storeTimestamp(getBuildTimestampFile(), getBuildTimestamp());

        BuildService.BuildContext buildContext = getBuildContext();
        ImagePullManager pullManager = getImagePullManager(determinePullPolicy(imageConfig.getBuildConfiguration()), autoPull);
        BuildService buildService = hub.getBuildService();

        buildService.buildImage(imageConfig, pullManager, buildContext);
        if (!skipTag) {
            buildService.tagImage(imageConfig.getName(), imageConfig);
        }
    }

    // We ignore an already existing date file and always return the current date
    @Override
    protected Date getReferenceDate() throws MojoExecutionException {
        return new Date();
    }

    private String determinePullPolicy(BuildImageConfiguration buildConfig) {
        return buildConfig != null && buildConfig.getImagePullPolicy() != null ? buildConfig.getImagePullPolicy() : imagePullPolicy;
    }

    /**
     * Helper method to process an ImageConfiguration.
     *
     * @param hub ServiceHub
     * @param aImageConfig ImageConfiguration that would be forwarded to build and tag
     * @throws DockerAccessException
     * @throws MojoExecutionException
     */
    private void processImageConfig(ServiceHub hub, ImageConfiguration aImageConfig) throws DockerAccessException, MojoExecutionException {
        BuildImageConfiguration buildConfig = aImageConfig.getBuildConfiguration();

        if (buildConfig != null) {
            if(buildConfig.skip()) {
                log.info("%s : Skipped building", aImageConfig.getDescription());
            } else {
                buildAndTag(hub, aImageConfig);
            }
        }
    }
}
