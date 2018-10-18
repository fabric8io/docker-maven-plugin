package io.fabric8.maven.docker.build;

import java.io.IOException;

import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.ImagePullPolicy;

/**
 * @author roland
 * @since 17.10.18
 */
public interface RegistryService {
    void pushImage(ImageConfiguration imageConfig, int retries, boolean skipTag, RegistryContext registryContext) throws IOException;

    void pullImage(String image, ImagePullPolicy policy, RegistryContext registryContext) throws IOException;
}
