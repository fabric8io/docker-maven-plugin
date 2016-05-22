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
     * Debug message if debugging is enabled.
     *
     * @param msg message to print
     */
    void debug(String msg);


    /**
     * Informational message
     *
     * @param format info message format
     * @param params parameter for formatting message
     */
    void info(String format, Object ... params);

    /**
     * Informational message
     *
     * @param message to print
     */
    void info(String message);

    /**
     * Verbose message
     *
     * @param format verbose message format
     * @param params parameter for formatting message
     */
    void verbose(String format, Object ... params);

    /**
     * A warning.
     *
     * @param format warning message format
     * @param params parameter for formatting message
     */
    void warn(String format, Object ... params);

    /**
     * A warning.
     *
     * @param message to print
     */
    void warn(String message);

    /**
     * Severe errors
     *
     * @param format error message format
     * @param params parameter for formatting message
     */
    void error(String format, Object ... params);

    /**
     * Severe errors
     *
     * @param message to print
     */
    void error(String message);

    /**
     * Prepare the given message as an error message
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
}
