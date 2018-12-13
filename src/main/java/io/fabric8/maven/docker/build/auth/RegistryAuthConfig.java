package io.fabric8.maven.docker.build.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * @author roland
 * @since 22.10.18
 */
public class RegistryAuthConfig {

    private Map<String, Map<String, String>> handlerConfig = new HashMap<>();
    private Map<Kind, Map<String, String>> kindConfig = new HashMap<>();
    private Map<String, String> defaultConfig = new HashMap<>();

    private boolean skipExtendedAuthentication = false;

    private String propertyPrefix;

    private RegistryAuthConfig() { }

    public String getConfigForHandler(String handlerName, String key) {
        return Optional.ofNullable(handlerConfig.get(handlerName)).map(m -> m.get(key)).orElse(null);
    }

    public String getUsername(Kind kind) {
        return getValueWithFallback(kind, RegistryAuth.USERNAME);
    }

    public String getPassword(Kind kind) {
        return getValueWithFallback(kind, RegistryAuth.PASSWORD);
    }

    public String getEmail(Kind kind) {
        return getValueWithFallback(kind, RegistryAuth.EMAIL);
    }

    public String getAuth(Kind kind) {
        return getValueWithFallback(kind, RegistryAuth.AUTH);
    }

    private String getValueWithFallback(Kind kind, String key) {
        return Optional.ofNullable(kindConfig.get(kind)).map(m -> m.get(key)).orElse(defaultConfig.get(key));
    }

    public boolean skipExtendedAuthentication() {
        return skipExtendedAuthentication;
    }

    public String extractFromProperties(Properties properties, Kind kind, String key) {
        String value = properties.getProperty(propertyPrefix + "." + kind.name().toLowerCase() + "." + key);
        if (value != null) {
            return value;
        }
        // Default is without kind
        return properties.getProperty(propertyPrefix + "." + key);
    }


    public static class Builder {

        private final RegistryAuthConfig config;

        public Builder() {
            config = new RegistryAuthConfig();
        }

        public Builder addKindConfig(Kind kind, String key, String value) {
            config.kindConfig.computeIfAbsent(kind, k -> new HashMap<>()).put(key, value);
            return this;
        }

        public Builder addDefaultConfig(String key, String value) {
            if (value != null) {
                config.defaultConfig.put(key, value);
            }
            return this;
        }

        public Builder addHandlerConfig(String id, String key, String value) {
            if (value != null) {
                config.handlerConfig.computeIfAbsent(id, i -> new HashMap<>()).put(key, value);
            }
            return this;
        }

        public Builder skipExtendedAuthentication(boolean skipExtendedAuthentication) {
            config.skipExtendedAuthentication = skipExtendedAuthentication;
            return this;
        }

        public Builder propertyPrefix(String propertyPrefix) {
            config.propertyPrefix = propertyPrefix;
            return this;
        }

        public RegistryAuthConfig build() {
            return config;
        }
    }

    // ========================================================================================

    public enum Kind {
        PUSH,
        PULL;
    }
}
