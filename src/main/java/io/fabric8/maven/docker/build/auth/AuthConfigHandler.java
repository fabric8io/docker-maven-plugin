package io.fabric8.maven.docker.build.auth;

import java.io.IOException;

/**
 * @author roland
 * @since 21.10.18
 */
public interface AuthConfigHandler {

    AuthConfig create(LookupMode mode, String user, String registry, Decryptor decryptor);

    interface Extender {
        AuthConfig extend(AuthConfig given, String registry) throws IOException;
    }

    interface Decryptor {
        String decrypt(String value);
    }

    enum LookupMode {
        PUSH("docker.push." , "push"),
        PULL("docker.pull.", "pull"),
        DEFAULT("docker.", null);

        private final String sysPropPrefix;
        private String configMapKey;

        LookupMode(String sysPropPrefix, String configMapKey) {
            this.sysPropPrefix = sysPropPrefix;
            this.configMapKey = configMapKey;
        }

        public String asSysProperty(String prop) {
            return sysPropPrefix + prop;
        }

        public String getConfigKey() {
            return configMapKey;
        }
    }
}
















