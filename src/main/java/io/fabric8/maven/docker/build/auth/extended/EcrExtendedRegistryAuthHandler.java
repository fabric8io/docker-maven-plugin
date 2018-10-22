package io.fabric8.maven.docker.build.auth.extended;

import java.io.IOException;

import io.fabric8.maven.docker.build.auth.RegistryAuth;
import io.fabric8.maven.docker.build.auth.RegistryAuthHandler;
import io.fabric8.maven.docker.build.auth.extended.ecr.EcrExtendedAuth;
import io.fabric8.maven.docker.util.Logger;

/**
 * @author roland
 * @since 21.10.18
 */
public class EcrExtendedRegistryAuthHandler implements RegistryAuthHandler.Extender {

    private final Logger log;

    public EcrExtendedRegistryAuthHandler(Logger log) {
        this.log = log;
    }

    @Override
    public String getId() {
        return "ecr";
    }

    public RegistryAuth extend(RegistryAuth given, String registry) throws IOException {
        EcrExtendedAuth ecr = new EcrExtendedAuth(log, registry);
        if (ecr.isAwsRegistry()) {
            return ecr.extendedAuth(given);
        }
        return given;
    }
}
