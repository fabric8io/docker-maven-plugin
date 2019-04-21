package io.fabric8.maven.docker.util;


/**
 * Simple log handler for printing used during the maven build
 *
 * @author roland
 * @since 31.03.14
 */
public interface Logger {

    /**
     * Debug message if debugging is enabled.
     *
     * @param format debug message format
     * @param params parameter for formatting message
     */
    void debug(String format, Object ... params);

    /**
     * Informational message
     *
     * @param format info message format
     * @param params parameter for formatting message
     */
    void info(String format, Object ... params);

    /**
     * Verbose message for build
     *
     * @param logVerboseCategory debug level for logging
     * @param format verbose message format
     * @param params parameter for formatting message
     */
    void verbose(Logger.LogVerboseCategory logVerboseCategory, String format, Object ... params);

    /**
     * A warning.
     *
     * @param format warning message format
     * @param params parameter for formatting message
     */
    void warn(String format, Object ... params);

    /**
     * Severe errors
     *
     * @param format error message format
     * @param params parameter for formatting message
     */
    void error(String format, Object ... params);

    /**
     * Prepare the given message as an error message to be used in exceptions.
     *
     * @param message message to prepare
     * @return prepared error message
     */
    String errorMessage(String message);

    /**
     * Whether debugging is enabled.
     */
    boolean isDebugEnabled();

    /**
     * Whether verbose is enablee
     */
    boolean isVerboseEnabled();

    /**
     * Start a progress bar* @param total the total number to be expected
     */
    void progressStart();

    /**
     * Update the progress
     *
     * @param layerId the image id of the layer fetched
     * @param status a status message
     * @param progressMessage the progressBar
     */
    void progressUpdate(String layerId, String status, String progressMessage);

    /**
     * Finis progress meter. Must be always called if {@link #progressStart()} has been
     * used.
     */
    void progressFinished();

    enum LogVerboseCategory {
        BUILD("build"), API("api");

        private String category;

        LogVerboseCategory(String category) {
            this.category = category;
        }

        public String getValue() {
            return category;
        }
    };
}
