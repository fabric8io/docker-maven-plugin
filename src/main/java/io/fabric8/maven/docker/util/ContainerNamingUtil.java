package io.fabric8.maven.docker.util;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author marcus
 * @since 1.0.0
 */
public class ContainerNamingUtil {

    private static final String DEFAULT_NAMING_CONFIGURATION = "%n-%i";

    private static final String INDEX_PLACEHOLDER = "%i";

    // class with only static methods
    private ContainerNamingUtil() { }

    public static String calculateContainerName(final String containerNamePattern,
                                                final String imageName,
                                                final String nameAlias,
                                                final Date buildTimestamp,
                                                final Set<String> existingContainers) {

        final String partiallyApplied =
            replacePlaceholders(
                containerNamePattern != null ? containerNamePattern : DEFAULT_NAMING_CONFIGURATION,
                imageName,
                nameAlias,
                buildTimestamp);


        if (partiallyApplied.contains(INDEX_PLACEHOLDER)) {
            for (long i = 1; i < Long.MAX_VALUE; i++) {
                final String withIndexApplied = partiallyApplied.replaceAll(INDEX_PLACEHOLDER, String.valueOf(i));
                if (!existingContainers.contains(withIndexApplied)) {
                    return withIndexApplied;
                }
            }
            throw new IllegalStateException("Could not find any free container name for pattern " + partiallyApplied);
        } else {
            return partiallyApplied;
        }
    }

    public static String calculateLastContainerName(final String containerNamePattern,
                                                    final String imageName,
                                                    final String nameAlias,
                                                    final Date buildTimestamp,
                                                    final Set<String> existingContainers) {

        final String partiallyApplied =
            replacePlaceholders(containerNamePattern,
                                imageName,
                                nameAlias,
                                buildTimestamp);

        if (partiallyApplied.contains(INDEX_PLACEHOLDER)) {
            return applyLastIndex(existingContainers, partiallyApplied);
        } else {
            return partiallyApplied;
        }
    }

    // ========================================================================================================

    private static String replacePlaceholders(String containerNamePattern, String imageName, String nameAlias, Date buildTimestamp) {

        Map<String, FormatParameterReplacer.Lookup> lookups = new HashMap<>();

        lookups.put("a", () -> nameAlias);
        lookups.put("n", () -> cleanImageName(imageName));
        lookups.put("t", () -> String.valueOf(buildTimestamp.getTime()));
        lookups.put("i", () -> INDEX_PLACEHOLDER);

        return new FormatParameterReplacer(lookups).replace(containerNamePattern);
    }


    private static String applyLastIndex(Set<String> existingContainerNames, final String partiallyApplied) {

        String last = null;
        for (long i = 1; i < Long.MAX_VALUE; i++) {
            final String withIndexApplied = partiallyApplied.replaceAll(INDEX_PLACEHOLDER, String.valueOf(i));
            if (last == null) {
                last = withIndexApplied;
            }

            if (!existingContainerNames.contains(withIndexApplied)) {
                return last;
            } else {
                last = withIndexApplied;
            }
        }

        return last;
    }

    private static String cleanImageName(final String imageName) {
        return new ImageName(imageName).getSimpleName().replaceAll("[^a-zA-Z0-9_.-]+", "_");
    }
}
