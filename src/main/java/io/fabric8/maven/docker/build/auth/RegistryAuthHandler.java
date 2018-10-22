package io.fabric8.maven.docker.build.auth;

import java.io.IOException;
import java.util.function.Function;

/**
 * @author roland
 * @since 21.10.18
 */
public interface RegistryAuthHandler {

    String getId();

    RegistryAuth create(RegistryAuthConfig.Kind kind, String user, String registry, Function<String, String> decryptor);

    interface Extender {
        String getId();
        RegistryAuth extend(RegistryAuth given, String registry) throws IOException;
    }
}
















