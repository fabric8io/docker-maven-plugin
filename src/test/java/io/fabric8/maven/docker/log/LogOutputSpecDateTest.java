package io.fabric8.maven.docker.log;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.FormatStyle;
import java.util.stream.Stream;

import static java.time.format.DateTimeFormatter.ofLocalizedDateTime;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author denisa
 * @since 03.08.20
 */
class LogOutputSpecDateTest {
    // Mon Jan 2 15:04:05 PST 2006
    private static final ZonedDateTime DATE_TIME = ZonedDateTime.of(2006, 1, 2, 15, 4, 5, (int) MILLISECONDS.toNanos(7), ZoneOffset.ofHours(-8));

    private static Stream<Arguments> data() {
        return Stream.of(
            Arguments.of(null, ""),
            Arguments.of("none", ""),
            Arguments.of("false", ""),
            Arguments.of("default", "15:04:05.007 "),
            Arguments.of("iso8601", "2006-01-02T15:04:05.007-08:00 "),
            Arguments.of("short", DATE_TIME.format(ofLocalizedDateTime(FormatStyle.SHORT)) + " "),
            Arguments.of("medium", DATE_TIME.format(ofLocalizedDateTime(FormatStyle.MEDIUM)) + " "),
            Arguments.of("long", DATE_TIME.format(ofLocalizedDateTime(FormatStyle.LONG)) + " "),
            Arguments.of("YYYY MM", "2006 01 ")
        );
    }

    @ParameterizedTest(name = "{index}: format \"{0}\" --> \"{1}\"")
    @MethodSource("data")
    void prompt(String timeFormatter, String expectedPrompt) {
        LogOutputSpec spec = createSpec(timeFormatter);
        Assertions.assertEquals(expectedPrompt, spec.getPrompt(false, DATE_TIME));
    }

    private LogOutputSpec createSpec(String timeFormatter) {
        return new LogOutputSpec.Builder().timeFormatter(timeFormatter).prefix("").build();
    }
}
