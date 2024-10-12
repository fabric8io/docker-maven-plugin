package io.fabric8.maven.docker.access.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Partial implementation of the patterns from https://pkg.go.dev/maze.io/x/duration
 * This implementation doesn't support combinations of timeunits.
 */
public class ComposeDurationUtil {

    private ComposeDurationUtil() {
    }

    private static final Pattern SIMPLE_GO_DURATION_FORMAT = Pattern.compile("^([\\d]+)(ns|us|ms|s|m|h|d|w|y)?$");
    private static final Map<String, TimeUnit> GO_TYPES_TO_JAVA = new HashMap<>();

    static {
        GO_TYPES_TO_JAVA.put("ns", TimeUnit.NANOSECONDS);
        GO_TYPES_TO_JAVA.put("us", TimeUnit.MICROSECONDS);
        GO_TYPES_TO_JAVA.put("ms", TimeUnit.MILLISECONDS);
        GO_TYPES_TO_JAVA.put("s", TimeUnit.SECONDS);
        GO_TYPES_TO_JAVA.put("m", TimeUnit.MINUTES);
        GO_TYPES_TO_JAVA.put("h", TimeUnit.HOURS);
        GO_TYPES_TO_JAVA.put("d", TimeUnit.DAYS);
    }


    public static long goDurationToNanoseconds(String goDuration, String field) {
        requireNonNull(goDuration);

        Matcher matcher = SIMPLE_GO_DURATION_FORMAT.matcher(goDuration);
        if (!matcher.matches()) {
            String message = String.format("Unsupported duration value \"%s\" for the field \"%s\"", goDuration, field);
            throw new IllegalArgumentException(message);
        }
        long duration = Long.parseLong(matcher.group(1));
        if (matcher.groupCount() == 2 && matcher.group(2) != null) {
            String type = matcher.group(2);

            if (GO_TYPES_TO_JAVA.containsKey(type)) {
                duration = TimeUnit.NANOSECONDS.convert(duration, GO_TYPES_TO_JAVA.get(type));
            } else if ("w".equals(type)) {
                duration = 7 * TimeUnit.NANOSECONDS.convert(duration, TimeUnit.DAYS);
            } else if ("y".equals(type)) {
                duration = 365 * TimeUnit.NANOSECONDS.convert(duration, TimeUnit.DAYS);
            } else {
                throw new IllegalArgumentException("Unsupported time unit: " + type);
            }
        }

        return duration;
    }
}
