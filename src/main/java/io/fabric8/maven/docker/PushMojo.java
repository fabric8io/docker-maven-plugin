package io.fabric8.maven.docker;

import java.io.IOException;

import io.fabric8.maven.docker.build.maven.MavenRegistryContext;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

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

    /**
     * Skip building tags
     */
    @Parameter(property = "docker.skip.tag", defaultValue = "false")
    private boolean skipTag;

    @Parameter(property = "docker.push.retries", defaultValue = "0")
    private int retries;

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeInternal(ServiceHub hub) throws IOException {
        if (skipPush) {
            return;
        }

        MavenRegistryContext registryContext = new MavenRegistryContext.Builder()
            .authRegistryAuthFactory(registryAuthFactory)
            .pushRegistry(pushRegistry)
            .build();

        for (ImageConfiguration imageConfig : getResolvedImages()) {
            hub.getRegistryService().pushImage(imageConfig, retries, skipTag, registryContext);
        }
    }
}
