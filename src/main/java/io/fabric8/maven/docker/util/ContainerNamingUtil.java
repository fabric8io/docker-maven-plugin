package io.fabric8.maven.docker.util;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.model.Container;

/**
 * @author marcus
 * @since 1.0.0
 */
public class ContainerNamingUtil {

    static final String INDEX_PLACEHOLDER = "%i";
    static final String EMPTY_NAME_PLACEHOLDER = "%e";

    public static final String DEFAULT_CONTAINER_NAME_PATTERN = "%n-%i";

    // class with only static methods
    private ContainerNamingUtil() { }

    public static String formatContainerName(final ImageConfiguration image,
                                             final String defaultContainerNamePattern,
                                             final Date buildTimestamp,
                                             final Collection<Container> existingContainers) {

        String containerNamePattern = extractContainerNamePattern(image, defaultContainerNamePattern);
        Set<String> existingContainersNames = extractContainerNames(existingContainers);

        if (shouldUseEmptyName(containerNamePattern)) {
            return null;
        }

        String partiallyApplied = replacePlaceholders(
                        containerNamePattern,
                        image.getName(),
                        image.getAlias(),
                        buildTimestamp);

        if (partiallyApplied.contains(INDEX_PLACEHOLDER)) {
            for (long i = 1; i < Long.MAX_VALUE; i++) {
                final String withIndexApplied = partiallyApplied.replaceAll(INDEX_PLACEHOLDER, String.valueOf(i));
                if (!existingContainersNames.contains(withIndexApplied)) {
                    return withIndexApplied;
                }
            }
            throw new IllegalStateException("Could not find any free container name for pattern " + partiallyApplied);
        } else {
            return partiallyApplied;
        }
    }

    /**
     * Keep only the entry with the higest index if an indexed naming scheme for container has been chosen or if the container name
     * pattern doesn't contain any index placeholders then filter containers (analog
     * {@link #formatContainerName(ImageConfiguration, String, Date, Collection)} but with stopping them in mind).<br>
     *<br>
     *The placeholders of the containerNamePattern are resolved as follows:<br>
     *<ul>
     *<li>%a is replaced with the image alias</li>
     *<li>%n is replaced with the image name</li>
     *<li>%t is replaced with any date (regex \d{10,})</li>
     *<li>%i is replaced with any number (regex \d+)</li>
     *</ul>
     * @param image the image from which to the the container pattern
     * @param buildTimestamp the timestamp for the build
     * @param containers the list of existing containers
     * @return filtered container instances, maybe empty but never null
     */
    public static Collection<Container> getContainersToStop(final ImageConfiguration image,
                                                            final String defaultContainerNamePattern,
                                                            final Date buildTimestamp,
                                                            final Collection<Container> containers) {

        String containerNamePattern = extractContainerNamePattern(image, defaultContainerNamePattern);

        if (shouldUseEmptyName(containerNamePattern)) {
            return containers;
        }

        // if the pattern contains an index placeholder -> we have to stick to the old approach (backward compatibility)
        if (containerNamePattern.contains(INDEX_PLACEHOLDER)) {
            final String partiallyApplied =
                    replacePlaceholders(
                            containerNamePattern,
                            image.getName(),
                            image.getAlias(),
                            buildTimestamp);

            return keepOnlyLastIndexedContainer(containers, partiallyApplied);
        }

        return replacePlaceholdersAndFilterContainers(containers, image, containerNamePattern);
    }


    // ========================================================================================================

    private static Collection<Container> replacePlaceholdersAndFilterContainers(Collection<Container> containers, ImageConfiguration image,
            String containerNamePattern) {
        Map<String, FormatParameterReplacer.Lookup> lookups = new HashMap<>();
        lookups.put("a", () -> image.getAlias());
        lookups.put("n", () -> cleanImageName(image.getName()));
        lookups.put("t", () -> "\\d{10,}");
        lookups.put("i", () -> "\\d+");

        String appliedContainerNamePattern = new FormatParameterReplacer(lookups).replace(containerNamePattern);

        return containers.stream()
                .filter(container -> container.getName().matches(appliedContainerNamePattern))
                .collect(Collectors.toList());
    }

    private static String replacePlaceholders(String containerNamePattern, String imageName, String nameAlias, Date buildTimestamp) {

        Map<String, FormatParameterReplacer.Lookup> lookups = new HashMap<>();

        lookups.put("a", () -> nameAlias);
        lookups.put("n", () -> cleanImageName(imageName));
        lookups.put("t", () -> String.valueOf(buildTimestamp.getTime()));
        lookups.put("i", () -> INDEX_PLACEHOLDER);

        return new FormatParameterReplacer(lookups).replace(containerNamePattern);
    }


    // Filter out any older indexed containernames, keeping only the last one (i.e. with the highest index)
    private static Collection<Container> keepOnlyLastIndexedContainer(Collection<Container> existingContainers, final String partiallyApplied) {

        Collection<Container> result = new ArrayList<>(existingContainers);

        // No index place holder, so nothing to filters
        if (!partiallyApplied.contains(INDEX_PLACEHOLDER)) {
            return result;
        }

        Map<String,Container> containerMap = existingContainers.stream().collect(Collectors.toMap(Container::getName, Function.identity()));

        Container last = null;
        for (long i = 1; i < Long.MAX_VALUE; i++) {
            final String withIndexApplied = partiallyApplied.replaceAll(INDEX_PLACEHOLDER, String.valueOf(i));
            Container mapped = containerMap.get(withIndexApplied);
            if (mapped != null) {
                result.remove(mapped);
                last = mapped;
            } else {
                // Readd last one removed (if any)
                if (last != null) {
                    result.add(last);
                }
                return result;
            }
        }
        throw new IllegalStateException("Internal error: Cannot find a free container index slot in " + existingContainers);
    }

    private static Set<String> extractContainerNames(final Collection<Container> existingContainers) {
        final Set<String> containerNamesBuilder = new HashSet<>();
        for (final Container container : existingContainers) {
            containerNamesBuilder.add(container.getName());
        }

        return Collections.unmodifiableSet(containerNamesBuilder);
    }

    private static String extractContainerNamePattern(ImageConfiguration image, String defaultContainerNamePattern) {
        RunImageConfiguration runConfig = image.getRunConfiguration();
        if (runConfig != null) {
            if (runConfig.getContainerNamePattern() != null) {
                return runConfig.getContainerNamePattern();
            }
            if (runConfig.getNamingStrategy() == RunImageConfiguration.NamingStrategy.alias) {
                return "%a";
            }
        }
        return defaultContainerNamePattern != null ? defaultContainerNamePattern : DEFAULT_CONTAINER_NAME_PATTERN;
    }

    private static String cleanImageName(final String imageName) {
        return new ImageName(imageName).getSimpleName().replaceAll("[^a-zA-Z0-9_.-]+", "_");
    }

    private static boolean shouldUseEmptyName(String pattern) {
        if (pattern.contains(EMPTY_NAME_PLACEHOLDER) && !pattern.equals(EMPTY_NAME_PLACEHOLDER)) {
            throw new IllegalArgumentException("Invalid use of container naming pattern " + EMPTY_NAME_PLACEHOLDER);
        }
        return pattern.equals(EMPTY_NAME_PLACEHOLDER);
    }

}
