package io.fabric8.maven.docker.util;

import com.google.common.base.MoreObjects;
import io.fabric8.maven.docker.config.NamingStrategy;

/**
 * @author marcus
 * @since 1.0.0
 */
public abstract class ContainerName {

    private ContainerName() {
        // utility class
    }

    public static String calculate(String alias,
                                   NamingStrategy namingStrategy,
                                   NamingStrategy defaultNamingStrategy,
                                   String containerPrefix,
                                   String imageName) {
        final NamingStrategy activeNamingStrategy = MoreObjects.firstNonNull(namingStrategy, defaultNamingStrategy);

        switch (activeNamingStrategy) {
            case alias:
                if (alias == null) {
                    throw new IllegalArgumentException("A naming scheme 'alias' requires an image alias to be set");
                }
                return alias;
            case auto:
                if (containerPrefix == null) {
                    throw new IllegalArgumentException("A naming scheme 'auto' requires a container prefix to be set");
                }
                final String validImageName = new ImageName(imageName).getSimpleName().replaceAll("[^a-zA-Z0-9_.-]+", "_");
                return containerPrefix + "_" + validImageName;
            case none:
                return null;
            default:
                throw new IllegalArgumentException("Naming scheme '" + activeNamingStrategy + "' not handled");
        }
    }

}
