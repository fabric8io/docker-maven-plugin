package io.fabric8.maven.docker.build;

import java.io.IOException;

import io.fabric8.maven.docker.build.auth.AuthConfig;
import io.fabric8.maven.docker.config.build.ImagePullPolicy;

/**
 * @author roland
 * @since 17.10.18
 */
public interface RegistryContext {

    ImagePullPolicy getDefaultImagePullPolicy();

    String getPushRegistry();

    String getPullRegistry();

    AuthConfig getAuthConfig(boolean isPush, String user, String registry) throws IOException;

}
