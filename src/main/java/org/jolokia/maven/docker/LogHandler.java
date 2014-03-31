package org.jolokia.maven.docker;

/**
 * @author roland
 * @since 31.03.14
 */
interface LogHandler {

    void debug(String message);
    void info(String message);
    void warn(String message);
    void error(String message);

    boolean isDebugEnabled();

    void progressStart(int total);
    void progressUpdate(int current, int total, long start);
    void progressFinished();

}
