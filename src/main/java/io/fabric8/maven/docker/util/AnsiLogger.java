package io.fabric8.maven.docker.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
    public static final String DEFAULT_LOG_PREFIX = "DOCKER> ";
    private static final int NON_ANSI_UPDATE_PERIOD = 80;

    private final Log log;
    private final String prefix;
    private final boolean batchMode;

    private boolean isVerbose = false;
    private List<LogVerboseCategory> verboseModes = null;

    // ANSI escapes for various colors (or empty strings if no coloring is used)
    static Ansi.Color
            COLOR_ERROR = RED,
            COLOR_INFO = GREEN,
            COLOR_WARNING = YELLOW,
            COLOR_PROGRESS_ID = YELLOW,
            COLOR_PROGRESS_STATUS = GREEN,
            COLOR_PROGRESS_BAR = CYAN,
            COLOR_EMPHASIS = BLUE;


    // Map remembering lines
    private ThreadLocal<Map<String, Integer>> imageLines = new ThreadLocal<>();
    private ThreadLocal<AtomicInteger> updateCount = new ThreadLocal<>();

    // Whether to use ANSI codes
    private boolean useAnsi;


    public AnsiLogger(Log log, boolean useColor, String verbose) {
        this(log, useColor, verbose, false);
    }

    public AnsiLogger(Log log, boolean useColor, String verbose, boolean batchMode) {
        this(log, useColor, verbose, batchMode, DEFAULT_LOG_PREFIX);
    }

    public AnsiLogger(Log log, boolean useColor, String verbose, boolean batchMode, String prefix) {
        this.log = log;
        this.prefix = prefix;
        this.batchMode = batchMode;
        checkVerboseLoggingEnabled(verbose);
        initializeColor(useColor);
    }

    /** {@inheritDoc} */
    public void debug(String message, Object ... params) {
        if (isDebugEnabled()) {
            log.debug(prefix + format(message, params));
        }
    }

    /** {@inheritDoc} */
    public void info(String message, Object ... params) {
        log.info(colored(message, COLOR_INFO, true, params));
    }

    /** {@inheritDoc} */
    public void verbose(LogVerboseCategory logVerboseCategory, String message, Object ... params) {
        if (isVerbose && verboseModes != null && verboseModes.contains(logVerboseCategory)) {
            log.info(ansi().fgBright(BLACK).a(prefix).a(format(message, params)).reset().toString());
        }
    }

    /** {@inheritDoc} */
    public void warn(String format, Object ... params) {
        log.warn(colored(format, COLOR_WARNING, true, params));
    }

    /** {@inheritDoc} */
    public void error(String message, Object ... params) {
        log.error(colored(message, COLOR_ERROR, true, params));
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

    public boolean isVerboseEnabled() {
        return isVerbose;
    }

    /**
     * Start a progress bar
     */
    public void progressStart() {
        // A progress indicator is always written out to standard out if a tty is enabled.
        if (!batchMode && log.isInfoEnabled()) {
            imageLines.remove();
            updateCount.remove();
            imageLines.set(new HashMap<String, Integer>());
            updateCount.set(new AtomicInteger());
        }
    }

    /**
     * Update the progress
     */
    public void progressUpdate(String layerId, String status, String progressMessage) {
        if (!batchMode && log.isInfoEnabled() && StringUtils.isNotEmpty(layerId)) {
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

        // Status with progress bars: (max length = 11, hence pad to 11)
        // Extracting
        // Downloading
        String progress = progressMessage != null ? progressMessage : "";
        String msg =
            ansi()
                .fg(COLOR_PROGRESS_ID).a(imageId).reset().a(": ")
                .fg(COLOR_PROGRESS_STATUS).a(StringUtils.rightPad(status,11) + " ")
                .fg(COLOR_PROGRESS_BAR).a(progress).toString();
        println(msg);

        if (diff > 0) {
            // move cursor back down to bottom
            print(ansi().cursorDown(diff - 1).toString());
        }
    }

    private void updateNonAnsiProgress(String imageId) {
        AtomicInteger count = updateCount.get();
        int nr = count.getAndIncrement();
        if (nr % NON_ANSI_UPDATE_PERIOD == 0) {
            print("#");
        }
        if (nr > 0 && nr % (80 * NON_ANSI_UPDATE_PERIOD) == 0) {
            print("\n");
        }
    }

    /**
     * Finis progress meter. Must be always called if {@link #progressStart()} has been used.
     */
    public void progressFinished() {
        if (!batchMode && log.isInfoEnabled()) {
            imageLines.remove();
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
        this.useAnsi = useColor && !log.isDebugEnabled();
        if (useAnsi) {
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

    private String colored(String message, Ansi.Color color, boolean addPrefix, Object ... params) {
        Ansi ansi = ansi().fg(color);
        String msgToPrint = addPrefix ? prefix + message : message;
        return ansi.a(format(evaluateEmphasis(msgToPrint, color), params)).reset().toString();
    }

    // Use parameters when given, otherwise we use the string directly
    private String format(String message, Object[] params) {
        if (params.length == 0) {
            return message;
        } else if (params.length == 1 && params[0] instanceof Throwable) {
            // We print only the message here since breaking exception will bubble up
            // anyway
            return message + ": " + params[0].toString();
        } else {
            return String.format(message, params);
        }
    }

    // Emphasize parts encloses in "[[*]]" tags
    private String evaluateEmphasis(String message, Ansi.Color msgColor) {
        // Split but keep the content by splitting on [[ and ]] separately when they
        // are followed or preceded by their counterpart. This lets the split retain
        // the character in the center.
        String[] parts = message.split("(\\[\\[(?=.]])|(?<=\\[\\[.)]])");
        if (parts.length == 1) {
            return message;
        }
        // The split up string is comprised of a leading plain part, followed
        // by groups of colorization that are <SET> color-part <RESET> plain-part.
        // To avoid emitting needless color changes, we skip the set or reset
        // if the subsequent part is empty.
        String msgColorS = ansi().fg(msgColor).toString();
        StringBuilder ret = new StringBuilder(parts[0]);

        for (int i = 1; i < parts.length; i += 4) {
            boolean colorPart = i + 1 < parts.length && parts[i + 1].length() > 0;
            boolean plainPart = i + 3 < parts.length && parts[i + 3].length() > 0;

            if (colorPart) {
                ret.append(getEmphasisColor(parts[i]));
                ret.append(parts[i + 1]);
                if(plainPart) {
                    ret.append(msgColorS);
                }
            }
            if (plainPart) {
                ret.append(parts[i + 3]);
            }
        }
        return ret.toString();
    }

    private static final Map<String, Ansi.Color> COLOR_MAP = new HashMap<>();

    static {
        COLOR_MAP.put("*", COLOR_EMPHASIS);
        COLOR_MAP.put("B", BLUE);
        COLOR_MAP.put("C", CYAN);
        COLOR_MAP.put("Y", YELLOW);
        COLOR_MAP.put("G", GREEN);
        COLOR_MAP.put("M", MAGENTA);
        COLOR_MAP.put("R", RED);
        COLOR_MAP.put("W", WHITE);
        COLOR_MAP.put("S", BLACK);
        COLOR_MAP.put("D", DEFAULT);
    }

    private String getEmphasisColor(String id) {
        Ansi.Color color = COLOR_MAP.get(id.toUpperCase());
        if (color != null) {
            return id.toLowerCase().equals(id) ?
                // lower case letter means bright color ...
                ansi().fgBright(color).toString() :
                ansi().fg(color).toString();
        } else {
            return "";
        }
    }

    private void checkVerboseLoggingEnabled(String verbose) {
        if (verbose == null || verbose.equalsIgnoreCase("false")) {
            this.isVerbose = false;
            return;
        }
        if (verbose.equalsIgnoreCase("all")) {
            this.isVerbose = true;
            this.verboseModes = Arrays.asList(LogVerboseCategory.values());
            return;
        }
        if (verbose.equals("") || verbose.equalsIgnoreCase("true")) {
            this.isVerbose = true;
            this.verboseModes = Collections.singletonList(LogVerboseCategory.BUILD);
            return;
        }

        this.verboseModes = getVerboseModesFromString(verbose);
        this.isVerbose = true;
    }

    private Boolean checkBackwardVersionValues(String verbose) {
        if (verbose.isEmpty()) {
            return Boolean.TRUE;
        }
        if (verbose.equalsIgnoreCase("true") || verbose.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(verbose.toLowerCase());
        }
        return null;
    }

    private List<LogVerboseCategory> getVerboseModesFromString(String groups) {
        List<LogVerboseCategory> ret = new ArrayList<>();
        for (String group : groups.split(",")) {
            try {
                ret.add(LogVerboseCategory.valueOf(group.toUpperCase()));
            } catch (Exception exp) {
                log.info("log: Unknown verbosity group " + groups + ". Ignoring...");
            }
        }
        return ret;
    }
}
