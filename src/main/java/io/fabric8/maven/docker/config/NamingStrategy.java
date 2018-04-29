package io.fabric8.maven.docker.config;

/**
 * @author marcus
 * @since 1.0.0
 */ // Naming scheme for how to name container
public enum NamingStrategy {
    /**
     * No extra naming
     */
    none,
    /**
     * Use the alias as defined in the configuration
     */
    alias,
    /**
     * Use the image name plus a prefix
     */
    auto
}
