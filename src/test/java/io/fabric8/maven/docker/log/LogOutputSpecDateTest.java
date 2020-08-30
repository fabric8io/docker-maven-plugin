package io.fabric8.maven.docker.log;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static java.time.format.DateTimeFormatter.ofLocalizedDateTime;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;

/**
 * @author denisa
 * @since 03.08.20
 */
@RunWith(Parameterized.class)
public class LogOutputSpecDateTest {
    // Mon Jan 2 15:04:05 PST 2006
    private static final ZonedDateTime DATE_TIME = ZonedDateTime.of(2006, 1, 2, 15, 4, 5, (int) MILLISECONDS.toNanos(7), ZoneOffset.ofHours(-8));

    @Parameterized.Parameters(name = "{index}: format \"{0}\" --> \"{1}\"")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {null, ""},
                {"none", ""},
                {"false", ""},
                {"default", "15:04:05.007 "},
                {"iso8601", "2006-01-02T15:04:05.007-08:00 "},
                {"short", DATE_TIME.format(ofLocalizedDateTime(FormatStyle.SHORT)) + " "},
                {"medium", DATE_TIME.format(ofLocalizedDateTime(FormatStyle.MEDIUM)) + " "},
                {"long", DATE_TIME.format(ofLocalizedDateTime(FormatStyle.LONG)) + " "},
                {"YYYY MM", "2006 01 "},
        });
    }

    @Parameterized.Parameter(0)
    public String timeFormatter;

    @Parameterized.Parameter(1)
    public String expectedPrompt;

    @Test
    public void prompt() {
        LogOutputSpec spec = createSpec(timeFormatter);
        assertEquals(expectedPrompt, spec.getPrompt(false, DATE_TIME));
    }

    private LogOutputSpec createSpec(String timeFormatter) {
        return new LogOutputSpec.Builder().timeFormatter(timeFormatter).prefix("").build();
    }
}
