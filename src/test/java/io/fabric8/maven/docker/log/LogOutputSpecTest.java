package io.fabric8.maven.docker.log;

import org.fusesource.jansi.Ansi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author denisa
 * @since 03.08.20
 */
class LogOutputSpecTest {
    // Mon Jan 2 15:04:05 PST 2006
    private static final ZonedDateTime DATE_TIME = ZonedDateTime.of(2006, 1, 2, 15, 4, 5, (int) MILLISECONDS.toNanos(7), ZoneOffset.ofHours(-8));

    @Test
    void prompt() {
        final LogOutputSpec spec = new LogOutputSpec.Builder()
            .timeFormatter("").prefix("fcn> ")
            .build();
        Assertions.assertEquals("15:04:05.007 fcn> ", spec.getPrompt(false, DATE_TIME));
    }

    @Test
    void promptWithBrightColor() {
        final LogOutputSpec spec = new LogOutputSpec.Builder()
            .color("RED", true)
            .timeFormatter("").prefix("fcn> ")
            .build();

        boolean enabled = Ansi.isEnabled();
        Ansi.setEnabled(true);
        try {
            Assertions.assertEquals("\u001B[90m15:04:05.007\u001B[m \u001B[91mfcn> \u001B[m", spec.getPrompt(true, DATE_TIME));
        } finally {
            Ansi.setEnabled(enabled);
        }
    }

    @Test
    void promptWithColor() {
        final LogOutputSpec spec = new LogOutputSpec.Builder()
            .color("RED")
            .timeFormatter("").prefix("fcn> ")
            .build();

        boolean enabled = Ansi.isEnabled();
        Ansi.setEnabled(true);
        try {
            Assertions.assertEquals("\u001B[90m15:04:05.007\u001B[m \u001B[31mfcn> \u001B[m", spec.getPrompt(true, DATE_TIME));
        } finally {
            Ansi.setEnabled(enabled);
        }
    }

    @Test
    void unrecognizedColor() {
        LogOutputSpec.Builder builder = new LogOutputSpec.Builder();
        IllegalArgumentException iae = Assertions.assertThrows(IllegalArgumentException.class, () -> builder.color("glitter"));
        Assertions.assertTrue(iae.getMessage().startsWith("Invalid color"));
    }

    @Test
    void unrecognizedTimeFormat() {
        LogOutputSpec.Builder builder = new LogOutputSpec.Builder();
        IllegalArgumentException iae = Assertions.assertThrows(IllegalArgumentException.class, () -> builder.timeFormatter("This is not a format"));
        Assertions.assertTrue(iae.getMessage().startsWith("Cannot parse log date specification"));
    }
}
