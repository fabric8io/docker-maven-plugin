package io.fabric8.maven.docker.build.auth.extended;

import java.io.IOException;

import io.fabric8.maven.docker.build.auth.AuthConfig;
import io.fabric8.maven.docker.build.auth.AuthConfigHandler;
import io.fabric8.maven.docker.build.auth.extended.ecr.EcrExtendedAuth;
import io.fabric8.maven.docker.util.Logger;

/**
 * @author roland
 * @since 21.10.18
 */
public class EcrExtendedAuthConfigHandler implements AuthConfigHandler.Extender {

    private final Logger log;

    public EcrExtendedAuthConfigHandler(Logger log) {
        this.log = log;
    }

    public AuthConfig extend(AuthConfig given, String registry) throws IOException {
        EcrExtendedAuth ecr = new EcrExtendedAuth(log, registry);
        if (ecr.isAwsRegistry()) {
            return ecr.extendedAuth(given);
        }
        return given;
    }
}
