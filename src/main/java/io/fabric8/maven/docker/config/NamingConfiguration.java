package io.fabric8.maven.docker.config;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.util.ImageName;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Matcher.quoteReplacement;

/**
 * @author marcus
 * @since 1.0.0
 */
public class NamingConfiguration {

    private static final String DEFAULT_NAMING_CONFIGURATION = "%n-%i";

    private static final String ALIAS_PLACEHOLDER = "%a";
    private static final String IMAGE_NAME_PLACEHOLDER = "%n";
    private static final String TIMESTAMP_PLACEHOLDER = "%t";
    private static final String INDEX_PLACEHOLDER = "%i";

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("(" +
            Joiner.on('|').join(
                    ALIAS_PLACEHOLDER,
                    IMAGE_NAME_PLACEHOLDER,
                    TIMESTAMP_PLACEHOLDER,
                    INDEX_PLACEHOLDER)
            + ")");


    private final String containerNamePattern;
    private final String imageName;
    private final String nameAlias;
    private final String buildTimestamp;
    private final Set<String> existingContainerNames;

    @VisibleForTesting
    NamingConfiguration(final String containerNamePattern,
                                final String imageName,
                                final String nameAlias,
                                final String buildTimestamp,
                                final Set<String> existingContainerNames) {
        this.containerNamePattern = containerNamePattern;
        this.imageName = imageName;
        this.nameAlias = nameAlias;
        this.buildTimestamp = buildTimestamp;
        this.existingContainerNames = existingContainerNames;
    }

    public static NamingConfiguration create(final String containerNamePattern,
                                             final RunImageConfiguration.NamingStrategy namingStrategy,
                                             final String imageName,
                                             final String nameAlias,
                                             final Date buildTimestamp,
                                             final Collection<Container> existingContainers) {

        final String timestamp = String.valueOf(buildTimestamp.getTime());
        final Set<String> containerNames = extractContainerNames(existingContainers);

        // explicitly set containerNamePattern wins
        if (StringUtils.isNotBlank(containerNamePattern)) {
            return new NamingConfiguration(containerNamePattern, imageName, nameAlias, timestamp, containerNames);
        }

        // backward compatibility code
        if (namingStrategy != null) {
            switch (namingStrategy) {
                case alias:
                    return new NamingConfiguration(ALIAS_PLACEHOLDER, imageName, nameAlias, timestamp, containerNames);
                case none:
                    return new NamingConfiguration(DEFAULT_NAMING_CONFIGURATION, imageName, nameAlias, timestamp, containerNames);
                default:
                    throw new IllegalStateException("Naming strategy " + namingStrategy + "not covered in comptatibility code");
            }
        }

        return new NamingConfiguration(DEFAULT_NAMING_CONFIGURATION, imageName, nameAlias, timestamp, containerNames);
    }

    private static Set<String> extractContainerNames(final Collection<Container> existingContainers) {
        final ImmutableSet.Builder<String> containerNamesBuilder = ImmutableSet.builder();
        for (final Container container : existingContainers) {
            containerNamesBuilder.add(container.getName());
        }
        return containerNamesBuilder.build();
    }

    public String calculateContainerName() {
        final String partiallyApplied = calculateWithoutIndex();

        if (partiallyApplied.contains(INDEX_PLACEHOLDER)) {
            return applyIndexReplacement(partiallyApplied);
        } else {
            return partiallyApplied;
        }
    }

    private String calculateWithoutIndex() {
        final Matcher matcher = PLACEHOLDER_PATTERN.matcher(containerNamePattern);
        final StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            final String placeholder = matcher.group(1);

            switch (placeholder) {
                case ALIAS_PLACEHOLDER:
                    matcher.appendReplacement(sb, quoteReplacement(nameAlias));
                    break;
                case IMAGE_NAME_PLACEHOLDER:
                    matcher.appendReplacement(sb, quoteReplacement(cleanImageName(imageName)));
                    break;
                case TIMESTAMP_PLACEHOLDER:
                    matcher.appendReplacement(sb, quoteReplacement(buildTimestamp));
                    break;
                case INDEX_PLACEHOLDER:
                    // noop, we apply the index later
                    matcher.appendReplacement(sb, quoteReplacement(INDEX_PLACEHOLDER));
                    break;
                default:
                    throw new IllegalArgumentException("Matched an unexpected placeholder " + placeholder);
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private String applyIndexReplacement(final String partiallyApplied) {
        for (long i = 1; i < Long.MAX_VALUE; i++) {
            final String withIndexApplied = partiallyApplied.replaceAll(INDEX_PLACEHOLDER, String.valueOf(i));
            if (!existingContainerNames.contains(withIndexApplied)) {
                return withIndexApplied;
            }
        }
        throw new IllegalStateException("Could not find any free container name for pattern " + partiallyApplied);
    }

    private String applyLastIndex(final String partiallyApplied) {
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

    private String cleanImageName(final String imageName) {
        return new ImageName(imageName).getSimpleName().replaceAll("[^a-zA-Z0-9_.-]+", "_");
    }

    public String calculateLastContainerName() {
        final String partiallyApplied = calculateWithoutIndex();

        if (partiallyApplied.contains(INDEX_PLACEHOLDER)) {
            return applyLastIndex(partiallyApplied);
        } else {
            return partiallyApplied;
        }
    }
}
