package io.fabric8.maven.docker.build;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.config.build.ImagePullPolicy;

/**
 * @author roland
 * @since 17.10.18
 */
public interface RegistryContext {

    ImagePullPolicy getDefaultImagePullPolicy();

    String getPushRegistry();

    String getPullRegistry();

    AuthConfig lookupRegistryAuthConfig(boolean isPush, String user, String registry);

}
