package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.service.ServiceHub;

/**
 * Goal for pushing a data-docker container
 *
 * @author roland
 */
@Mojo(name = "push", defaultPhase = LifecyclePhase.DEPLOY)
public class PushMojo extends AbstractDockerMojo {

    // Registry to use for push operations if no registry is specified
    @Parameter(property = "docker.push.registry")
    private String pushRegistry;

    @Parameter(property = "docker.skip.push", defaultValue = "false")
    private boolean skipPush;

    @Parameter(property = "docker.push.retries", defaultValue = "0")
    private int retries;

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        if (skipPush) {
            return;
        }
        for (ImageConfiguration imageConfig : getResolvedImages()) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            String name = imageConfig.getName();
            if (buildConfig != null) {
                String configuredRegistry = getConfiguredRegistry(imageConfig, pushRegistry);
                AuthConfig authConfig = prepareAuthConfig(new ImageName(name), configuredRegistry, true);

                DockerAccess docker = hub.getDockerAccess();

                long start = System.currentTimeMillis();
                docker.pushImage(name, authConfig, configuredRegistry, retries);
                log.info("Pushed %s in %s", name, EnvUtil.formatDurationTill(start));

                for (String tag : imageConfig.getBuildConfiguration().getTags()) {
                    if (tag != null) {
                        docker.pushImage(new ImageName(name, tag).getFullName(), authConfig, configuredRegistry, retries);
                    }
                }
            }
        }
    }
}
