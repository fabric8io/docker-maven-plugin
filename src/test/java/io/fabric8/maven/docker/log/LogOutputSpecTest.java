package io.fabric8.maven.docker.log;

import org.fusesource.jansi.Ansi;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;

/**
 * @author denisa
 * @since 03.08.20
 */
public class LogOutputSpecTest {
    // Mon Jan 2 15:04:05 PST 2006
    private static final ZonedDateTime DATE_TIME = ZonedDateTime.of(2006, 1, 2, 15, 4, 5, (int) MILLISECONDS.toNanos(7), ZoneOffset.ofHours(-8));

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void prompt() {
        final LogOutputSpec spec = new LogOutputSpec.Builder()
                .timeFormatter("").prefix("fcn> ")
                .build();
        assertEquals("15:04:05.007 fcn> ", spec.getPrompt(false, DATE_TIME));
    }

    @Test
    public void promptWithBrightColor() {
        final LogOutputSpec spec = new LogOutputSpec.Builder()
                .color("RED", true)
                .timeFormatter("").prefix("fcn> ")
                .build();

        boolean enabled = Ansi.isEnabled();
        Ansi.setEnabled(true);
        try {
            assertEquals("\u001B[90m15:04:05.007\u001B[m \u001B[91mfcn> \u001B[m", spec.getPrompt(true, DATE_TIME));
        } finally {
            Ansi.setEnabled(enabled);
        }
    }

    @Test
    public void promptWithColor() {
        final LogOutputSpec spec = new LogOutputSpec.Builder()
                .color("RED")
                .timeFormatter("").prefix("fcn> ")
                .build();

        boolean enabled = Ansi.isEnabled();
        Ansi.setEnabled(true);
        try {
            assertEquals("\u001B[90m15:04:05.007\u001B[m \u001B[31mfcn> \u001B[m", spec.getPrompt(true, DATE_TIME));
        } finally {
            Ansi.setEnabled(enabled);
        }
    }

    @Test
    public void unrecognizedColor() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Invalid color");
        new LogOutputSpec.Builder().color("glitter").build();
    }

    @Test
    public void unrecognizedTimeFormat() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Cannot parse log date specification");
        new LogOutputSpec.Builder().timeFormatter("This is not a format").build();
    }
}
