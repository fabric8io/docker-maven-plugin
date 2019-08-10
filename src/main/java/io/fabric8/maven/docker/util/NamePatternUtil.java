package io.fabric8.maven.docker.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Helper functions for pattern matching for image and container names.
 */
public class NamePatternUtil {
    /// Name patterns can be prefixed with image= to have them only apply to image name.
    public static final String IMAGE_FIELD = "image";

    /// Name patterns can be prefixed with name= to have them only apply to container name.
    public static final String NAME_FIELD = "name";

    /**
     * Accepts an Ant-ish or regular expression pattern and compiles to a regular expression.
     *
     * This is similar to SelectorUtils in the Maven codebase, but there the code uses the
     * platform File.separator, while here we always want to work with forward slashes.
     * Also, for a more natural fit with repository tags, both * and ** should stop at the colon
     * that precedes the tag.
     *
     * Like SelectorUtils, wrapping a pattern in %regex[pattern] will create a regex from the
     * pattern provided without translation. Otherwise, or if wrapped in %ant[pattern],
     * then a regular expression will be created that is anchored at beginning and end,
     * converts ? to [^/:], * to ([^/:]|:(?=.*:)) and ** to ([^:]|:(?=.*:))*.
     *
     * If ** is followed by /, the / is converted to a negative lookbehind for anything
     * apart from a slash.
     *
     * @return a regular expression pattern created from the input pattern
     */
    public static String convertNamePattern(String pattern) {
        final String REGEX_PREFIX = "%regex[", ANT_PREFIX = "%ant[", PATTERN_SUFFIX="]";

        if(pattern.startsWith(REGEX_PREFIX) && pattern.endsWith(PATTERN_SUFFIX)) {
            return pattern.substring(REGEX_PREFIX.length(), pattern.length() - PATTERN_SUFFIX.length());
        }

        if(pattern.startsWith(ANT_PREFIX) && pattern.endsWith(PATTERN_SUFFIX)) {
            pattern = pattern.substring(ANT_PREFIX.length(), pattern.length() - PATTERN_SUFFIX.length());
        }

        String[] parts = pattern.split("((?=[/:?*])|(?<=[/:?*]))");
        Matcher matcher = Pattern.compile("[A-Za-z0-9-]+").matcher("");

        StringBuilder builder = new StringBuilder("^");

        for(int i = 0; i < parts.length; ++i) {
            if("?".equals(parts[i])) {
                builder.append("[^/:]");
            } else if("*".equals(parts[i])) {
                if (i + 1 < parts.length && "*".equals(parts[i + 1])) {
                    builder.append("([^:]|:(?=.*:))*");
                    ++i;
                    if (i + 1 < parts.length && "/".equals(parts[i + 1])) {
                        builder.append("(?<![^/])");
                        ++i;
                    }
                } else {
                    builder.append("([^/:]|:(?=.*:))*");
                }
            } else if("/".equals(parts[i]) || ":".equals(parts[i]) || matcher.reset(parts[i]).matches()) {
                builder.append(parts[i]);
            } else if(parts[i].length() > 0) {
                builder.append(Pattern.quote(parts[i]));
            }
        }

        builder.append("$");

        return builder.toString();
    }

    /**
     * Take a string that represents a list of patterns, possibly a mixture of
     * regular expressions and Ant-style patterns, and return a single regular
     * expression that matches any of the alternatives.
     *
     * To allow users to target some parts of the filter list to some fields on
     * the object to which the pattern is ultimately applied, it is possible to
     * prefix patterns in the list with fieldName=pattern, and then the pattern
     * can be excluded from use on unrelated fields.
     *
     * @param patternList the pattern list specification to convert
     *
     * @return the combined pattern, or null if there is are no patterns for
     * the field.
     *
     * @throws PatternSyntaxException if any of the individual patterns fails
     * to compile as a regular expression.
     */
    public static String convertNamePatternList(String patternList) {
        return convertNamePatternList(patternList, null, true);
    }

    /**
     * Take a string that represents a list of patterns, possibly a mixture of
     * regular expressions and Ant-style patterns, and return a single regular
     * expression that matches any of the alternatives.
     *
     * To allow users to target some parts of the filter list to some fields on
     * the object to which the pattern is ultimately applied, it is possible to
     * prefix patterns in the list with fieldName=pattern, and then the pattern
     * can be excluded from use on unrelated fields.
     *
     * @param patternList the pattern list specification to convert
     * @param fieldName the field name for which patterns should be selected
     * @param includeUnnamed if true, include patterns that do not specify a
     *                       field name
     *
     * @return the combined pattern, or null if there is are no patterns for
     * the field.
     *
     * @throws PatternSyntaxException if any of the individual patterns fails
     * to compile as a regular expression.
     */
    public static String convertNamePatternList(String patternList, String fieldName, boolean includeUnnamed) {
        String[] patterns = patternList.split(",");
        StringBuilder combinedPattern = new StringBuilder("(");
        boolean compound = false;

        for(String pattern : patterns) {
            pattern = pattern.trim();
            String[] namedFieldPattern = pattern.split("=", 2);
            if(namedFieldPattern.length == 2) {
                if(!namedFieldPattern[0].trim().equals(fieldName)) {
                    continue;
                }
                pattern = namedFieldPattern[1].trim();
            } else if(fieldName != null && !includeUnnamed) {
                continue;
            }

            if(pattern.length() > 0) {
                String converted = convertNamePattern(pattern);

                if(converted.length() == 0) {
                    continue;
                }

                try {
                    Pattern.compile(converted);
                } catch(PatternSyntaxException e) {
                    throw new IllegalArgumentException("Unable to convert pattern " + pattern + " to regular expression. " + e.getMessage(), e);
                }

                if(combinedPattern.length() > 1) {
                    compound = true;
                    combinedPattern.append('|');
                }

                combinedPattern.append(converted);
            }
        }

        if(compound) {
            combinedPattern.append(')');
        } else {
            combinedPattern.deleteCharAt(0);
        }

        return combinedPattern.length() == 0 ? null : combinedPattern.toString();
    }
}
