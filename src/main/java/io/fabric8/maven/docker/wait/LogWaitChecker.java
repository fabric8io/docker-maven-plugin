package io.fabric8.maven.docker.wait;

import java.util.regex.Pattern;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.log.LogCallback;
import io.fabric8.maven.docker.access.log.LogGetHandle;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.Timestamp;

/**
 * @author roland
 * @since 25/03/2017
 */
public class LogWaitChecker implements WaitChecker {

    private final String logPattern;
    private final ServiceHub hub;
    private final String containerId;
    private final Logger log;
    private boolean first;
    private LogGetHandle logHandle;
    // Flag updated from a different thread, hence volatile (see also #595)
    private volatile boolean detected;

    public LogWaitChecker(String pattern, ServiceHub hub, String containerId, Logger log) {
        this.log = log;
        this.logPattern = pattern;
        this.hub = hub;
        this.containerId = containerId;
        first = true;
        detected = false;
    }

    @Override
    public boolean check() {
        if (first) {
            final Pattern pattern = Pattern.compile(logPattern);
            log.debug("LogWaitChecker: Pattern to match '%s'", logPattern);
            DockerAccess docker = hub.getDockerAccess();
            logHandle = docker.getLogAsync(containerId, new LogMatchCallback(pattern));
            first = false;
        }
        return detected;
    }

    @Override
    public void cleanUp() {
        if (logHandle != null) {
            logHandle.finish();
        }
    }

    private class LogMatchCallback implements LogCallback {

        private final Pattern pattern;
        StringBuilder logBuffer;

        LogMatchCallback(Pattern pattern) {
            this.pattern = pattern;
            logBuffer = (pattern.flags() & Pattern.DOTALL) != 0 ? new StringBuilder() : null;
        }

        @Override
        public void log(int type, Timestamp timestamp, String txt) throws DoneException {
            log.debug("LogWaitChecker: Tying to match '%s' [Pattern: %s] [thread: %d]",
                      txt, logPattern, Thread.currentThread().getId());
            String toMatch;
            if (logBuffer != null) {
                logBuffer.append(txt);
                toMatch = logBuffer.toString();
            } else {
                toMatch = txt;
            }
            if (pattern.matcher(toMatch).find()) {
                detected = true;
                throw new DoneException();
            }
        }

        @Override
        public void error(String error) {
            log.error("%s", error);
        }

        @Override
        public void close() {
            // no-op
        }

        @Override
        public void open() {
            // no-op
        }
    }
}