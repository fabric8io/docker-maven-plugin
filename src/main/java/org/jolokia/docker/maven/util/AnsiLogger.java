package org.jolokia.docker.maven.util;

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.CYAN;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.RED;
import static org.fusesource.jansi.Ansi.Color.YELLOW;

import org.apache.maven.plugin.logging.Log;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

/**
 * Simple log handler for printing used during the maven build
 *
 * @author roland
 * @since 31.03.14
 */
public class AnsiLogger {

    // prefix used for console output
    private static final String LOG_PREFIX = "DOCKER> ";

    private final Log log;

    private int oldProgress = 0;
    private int total = 0;

    // ANSI escapes for various colors (or empty strings if no coloring is used)
    private static Ansi.Color
            COLOR_ERROR = RED,
            COLOR_INFO = GREEN,
            COLOR_WARNING = YELLOW,
            COLOR_PROGRESS = CYAN;

    public AnsiLogger(Log log, boolean useColor) {
        this.log = log;
        initializeColor(useColor);
    }

    public static String colorInfo(String message, boolean addPrefix) {
        return addColor(message, COLOR_INFO, addPrefix);
    }

    public static String colorWarning(String message, boolean addPrefix) {
        return addColor(message, COLOR_WARNING, addPrefix);
    }

    public static String colorError(String message, boolean addPrefix) {
        return addColor(message, COLOR_ERROR, addPrefix);
    }

    /**
     * Debug message if debugging is enabled.
     *
     * @param message message to print out
     */
    public void debug(String message) {
        log.debug(LOG_PREFIX + message);
    }

    public void debug(String format, Object... args) {
        log.debug(LOG_PREFIX + String.format(format, args));
    }
    
    /**
     * Informational message
     *
     * @param message info
     */
    public void info(String message) {
        log.info(colorInfo(message, true));
    }

    /**
     * A warning.
     *
     * @param message warning
     */
    public void warn(String message) {
        log.warn(colorWarning(message, true));
    }

    /**
     * Severe errors
     *
     * @param message error
     */
    public void error(String message) {
        log.error(colorError(message, true));
    }

    /**
     * Whether debugging is enabled.
     */
    public boolean isDebugEnabled() {
        return log.isDebugEnabled();
    }

    /**
     * Start a progress bar
     * 
     * @param total the total number to be expected
     */
    public void progressStart(int total) {
        // A progress indicator is always written out to standard out if a tty is enabled.
        if (log.isInfoEnabled()) {
            print(ansi().fg(COLOR_PROGRESS) + "       ");
            oldProgress = 0;
            this.total = total;
        }
    }

    /**
     * Update the progress
     *
     * @param current the current number to be expected
     */
    public void progressUpdate(int current) {
        if (log.isInfoEnabled()) {
            print("=");
            int newProgress = (current * 10 + 5) / total;
            if (newProgress > oldProgress) {
                print(" " + newProgress + "0% ");
                oldProgress = newProgress;
            }
            flush();
        }
    }

    /**
     * Finis progress meter. Must be always called if {@link #progressStart(int)} has been used.
     */
    public void progressFinished() {
        if (log.isInfoEnabled()) {
            println(ansi().reset().toString());
            oldProgress = 0;
            total = 0;
        }
    }
    
    private void flush() {
        System.out.flush();
    }
    
    private void initializeColor(boolean useColor) {
        if (System.console() == null || log.isDebugEnabled()) {
            useColor = false;
        }
        
        if (useColor) {
            AnsiConsole.systemInstall();
            Ansi.setEnabled(true);
        }
        else {
            Ansi.setEnabled(false);
        }
    }
    
    private void println(String txt) {
        System.out.println(txt);
    }

    private void print(String txt) {
        System.out.print(txt);
    }
    
    private static String addColor(String message, Ansi.Color color, boolean addPrefix) { 
        Ansi ansi = ansi().fg(color);
        if (addPrefix) {
            ansi.a(LOG_PREFIX);
        }
        return ansi.a(message).reset().toString();
    }
}
