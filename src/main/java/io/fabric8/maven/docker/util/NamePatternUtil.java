package io.fabric8.maven.docker.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper functions for pattern matching for image and container names.
 */
public class NamePatternUtil {

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
    public static String convertImageNamePattern(String pattern) {
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
}
