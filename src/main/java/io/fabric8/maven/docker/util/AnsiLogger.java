package io.fabric8.maven.docker.util;

import java.util.HashMap;
import java.util.Map;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.StringUtils;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import static org.fusesource.jansi.Ansi.Color.*;
import static org.fusesource.jansi.Ansi.ansi;

/**
 * Simple log handler for printing used during the maven build
 *
 * @author roland
 * @since 31.03.14
 */
public class AnsiLogger implements Logger {

    // prefix used for console output
    private static final String LOG_PREFIX = "DOCKER> ";

    private final Log log;

    private boolean verbose;

    // ANSI escapes for various colors (or empty strings if no coloring is used)
    private static Ansi.Color
            COLOR_ERROR = RED,
            COLOR_INFO = GREEN,
            COLOR_WARNING = YELLOW,
            COLOR_PROGRESS_ID = YELLOW,
            COLOR_PROGRESS_STATUS = GREEN,
            COLOR_PROGRESS_BAR = CYAN;

    // Map remembering lines
    private ThreadLocal<Map<String, Integer>> imageLines = new ThreadLocal<Map<String,Integer>>();

    // Old image id when used in non ansi mode
    private String oldImageId;

    // Whether to use ANSI codes
    private boolean useAnsi;

    public AnsiLogger(Log log, boolean useColor, boolean verbose) {
        this.log = log;
        this.verbose = verbose;
        initializeColor(useColor);
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
        log.info(colored(message, COLOR_INFO, true));
    }

    @Override
    public void verbose(String message) {
        if (verbose) {
            log.info(ansi().fgBright(BLACK).a(LOG_PREFIX).a(message).reset().toString());
        }
    }

    /**
     * A warning.
     *
     * @param message warning
     */
    public void warn(String message) {
        log.warn(colored(message, COLOR_WARNING, true));
    }

    /**
     * Severe errors
     *
     * @param message error
     */
    public void error(String message) {
        log.error(colored(message, COLOR_ERROR, true));
    }

    @Override
    public String errorMessage(String message) {
        return colored(message, COLOR_ERROR, false);
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
    public void progressStart() {
        // A progress indicator is always written out to standard out if a tty is enabled.
        if (log.isInfoEnabled()) {
            imageLines.remove();
            imageLines.set(new HashMap<String, Integer>());
            oldImageId = null;
        }
    }

    /**
     * Update the progress
     */
    public void progressUpdate(String layerId, String status, String progressMessage) {
        if (log.isInfoEnabled() && StringUtils.isNotEmpty(layerId)) {
            if (useAnsi) {
                updateAnsiProgress(layerId, status, progressMessage);
            } else {
                updateNonAnsiProgress(layerId);
            }
            flush();
        }
    }

    private void updateAnsiProgress(String imageId, String status, String progressMessage) {
        Map<String,Integer> imgLineMap = imageLines.get();
        Integer line = imgLineMap.get(imageId);

        int diff = 0;
        if (line == null) {
            line = imgLineMap.size();
            imgLineMap.put(imageId, line);
        } else {
            diff = imgLineMap.size() - line;
        }

        if (diff > 0) {
            print(ansi().cursorUp(diff).eraseLine(Ansi.Erase.ALL).toString());
        }

        String progress = progressMessage != null ? progressMessage : "";
        String msg =
            ansi()
                .fg(COLOR_PROGRESS_ID).a(imageId).reset().a(": ")
                .fg(COLOR_PROGRESS_STATUS).a(status + " ")
                .fg(COLOR_PROGRESS_BAR).a(progress).toString();
        println(msg);

        if (diff > 0) {
            // move cursor back down to bottom
            print(ansi().cursorDown(diff - 1).toString());
        }
    }

    private void updateNonAnsiProgress(String imageId) {
        if (!imageId.equals(oldImageId)) {
            print("\n" + imageId + ": .");
            oldImageId = imageId;
        } else {
            print(".");
        }
    }

    /**
     * Finis progress meter. Must be always called if {@link #progressStart()} has been used.
     */
    public void progressFinished() {
        if (log.isInfoEnabled()) {
            imageLines.remove();
            oldImageId = null;
            print(ansi().reset().toString());
            if (!useAnsi) {
                println("");
            }
        }
    }
    
    private void flush() {
        System.out.flush();
    }
    
    private void initializeColor(boolean useColor) {
        // sl4j simple logger used by Maven seems to escape ANSI escapes on Windows
        this.useAnsi = useColor && System.console() != null && !log.isDebugEnabled() && !isWindows();
        
        if (useAnsi) {
            AnsiConsole.systemInstall();
            Ansi.setEnabled(true);
        }
        else {
            Ansi.setEnabled(false);
        }
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().startsWith("windows");
    }

    private void println(String txt) {
        System.out.println(txt);
    }

    private void print(String txt) {
        System.out.print(txt);
    }

    private static String colored(String message, Ansi.Color color, boolean addPrefix) {
        Ansi ansi = ansi().fg(color);
        if (addPrefix) {
            ansi.a(LOG_PREFIX);
        }
        return ansi.a(message).reset().toString();
    }
}
