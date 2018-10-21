package io.fabric8.maven.docker.build.auth.handler;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.maven.docker.build.auth.AuthConfig;
import io.fabric8.maven.docker.build.auth.AuthConfigHandler;
import io.fabric8.maven.docker.util.Logger;

/**
 * @author roland
 * @since 21.10.18
 */
public class PluginConfigAuthConfigHandler implements AuthConfigHandler {
    private final Map registryAuthConfig;
    private final Logger log;

    public PluginConfigAuthConfigHandler(Map registryAuthConfig, Logger log) {
        this.registryAuthConfig = registryAuthConfig;
        this.log = log;
    }

    @Override
    public AuthConfig create(LookupMode mode, String user, String registry, Decryptor decryptor) {
        // Get configuration from global plugin config
        Map mapToCheck = getAuthConfigMapToCheck(mode, registryAuthConfig);

        if (mapToCheck != null && mapToCheck.containsKey(AuthConfig.AUTH_USERNAME)) {
            if (!mapToCheck.containsKey(AuthConfig.AUTH_PASSWORD)) {
                throw new IllegalArgumentException("No 'password' given while using <authConfig> in configuration for mode " + mode);
            }
            Map<String, String> cloneConfig = new HashMap<>(mapToCheck);
            cloneConfig.put(AuthConfig.AUTH_PASSWORD, decryptor.decrypt(cloneConfig.get(AuthConfig.AUTH_PASSWORD)));
            log.debug("AuthConfig: credentials from plugin config");
            return new AuthConfig(cloneConfig);
        }
        return null;
    }

    private Map getAuthConfigMapToCheck(LookupMode lookupMode, Map authConfigMap) {
        String configKey = lookupMode.getConfigKey();
        if (configKey == null) {
            return authConfigMap;
        }
        if (authConfigMap != null) {
            return (Map) authConfigMap.get(configKey);
        }
        return null;
    }
}
