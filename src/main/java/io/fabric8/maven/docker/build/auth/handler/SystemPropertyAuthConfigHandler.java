package io.fabric8.maven.docker.build.auth.handler;

import java.util.Properties;

import io.fabric8.maven.docker.build.auth.AuthConfig;
import io.fabric8.maven.docker.build.auth.AuthConfigHandler;
import io.fabric8.maven.docker.util.Logger;

/**
 * @author roland
 * @since 21.10.18
 */
public class SystemPropertyAuthConfigHandler implements AuthConfigHandler {

    Logger log;

    public SystemPropertyAuthConfigHandler(Logger log) {
        this.log = log;
    }

    @Override
    public AuthConfig create(LookupMode mode, String user, String registry, Decryptor decryptor) {
        AuthConfig result;
        Properties props = System.getProperties();
        String userKey = mode.asSysProperty(AuthConfig.AUTH_USERNAME);
        String passwordKey = mode.asSysProperty(AuthConfig.AUTH_PASSWORD);
        if (props.containsKey(userKey)) {
            if (!props.containsKey(passwordKey)) {
                throw new IllegalArgumentException("No " + passwordKey + " provided for username " + props.getProperty(userKey));
            }
            result = new AuthConfig(props.getProperty(userKey),
                                    decryptor.decrypt(props.getProperty(passwordKey)),
                                    props.getProperty(mode.asSysProperty(AuthConfig.AUTH_EMAIL)),
                                    props.getProperty(mode.asSysProperty(AuthConfig.AUTH_AUTHTOKEN)));
        } else {
            result = null;
        }
        AuthConfig ret = result;
        if (ret != null) {
            log.debug("AuthConfig: credentials from system properties");
        }
        return ret;
    }
}
