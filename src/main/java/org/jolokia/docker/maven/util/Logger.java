package org.jolokia.docker.maven.util;

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
     * @param message message to print out
     */
    void debug(String message);

    /**
     * Informational message
     *
     * @param message info
     */
    void info(String message);

    /**
     * Verbose messag
     *
     * @param message
     */
    void verbose(String message);

    /**
     * A warning.
     *
     * @param message warning
     */
    void warn(String message);

    /**
     * Severe errors
     *
     * @param message error
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
     * Start a progress bar
     * @param total the total number to be expected
     */
    void progressStart(int total);

    /**
     * Update the progress
     *
     * @param current the current number to be expected
     */
    void progressUpdate(int current);

    /**
     * Finis progress meter. Must be always called if {@link #progressStart(int)} has been
     * used.
     */
    void progressFinished();
}
