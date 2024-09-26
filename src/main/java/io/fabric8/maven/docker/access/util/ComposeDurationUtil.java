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
    private static Pattern SIMPLE_GO_DURATION_FORMAT = Pattern.compile("^([\\d]+)(ns|us|ms|s|m|h|d|w|y)?$");
    private static Map<String, TimeUnit> goTypesToJava = new HashMap<String, TimeUnit>() {{
        put("ns", TimeUnit.NANOSECONDS);
        put("us", TimeUnit.MICROSECONDS);
        put("ms", TimeUnit.MILLISECONDS);
        put("s", TimeUnit.SECONDS);
        put("m", TimeUnit.MINUTES);
        put("h", TimeUnit.HOURS);
        put("d", TimeUnit.DAYS);
    }};

    public static long goDurationToNanoseconds(String goDuration, String field) {
        requireNonNull(goDuration);

        Matcher matcher = SIMPLE_GO_DURATION_FORMAT.matcher(goDuration);
        if (!matcher.matches()) {
            String message = String.format("Unsupported duration value \"%s\" for the field \"%s\"", goDuration, field);
            throw new IllegalArgumentException(message);
        }
        long duration = Long.valueOf(matcher.group(1));
        if (matcher.groupCount() == 2 && matcher.group(2) != null) {
            String type = matcher.group(2);

            if (goTypesToJava.containsKey(type)) {
                duration = TimeUnit.NANOSECONDS.convert(duration, goTypesToJava.get(type));
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
