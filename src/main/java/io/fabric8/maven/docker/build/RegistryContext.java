package io.fabric8.maven.docker.build;

import java.io.IOException;

import io.fabric8.maven.docker.build.auth.RegistryAuth;
import io.fabric8.maven.docker.build.auth.RegistryAuthConfig;
import io.fabric8.maven.docker.config.build.ImagePullPolicy;

/**
 * @author roland
 * @since 17.10.18
 */
public interface RegistryContext {

    ImagePullPolicy getDefaultImagePullPolicy();

    String getRegistry(RegistryAuthConfig.Kind kind);

    RegistryAuth getAuthConfig(RegistryAuthConfig.Kind kind, String user, String registry) throws IOException;

}
