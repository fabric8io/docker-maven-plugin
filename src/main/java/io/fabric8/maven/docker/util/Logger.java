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
