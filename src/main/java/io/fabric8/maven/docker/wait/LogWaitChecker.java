package io.fabric8.maven.docker.wait;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.log.LogCallback;
import io.fabric8.maven.docker.access.log.LogGetHandle;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.Timestamp;

/**
 * @author roland
 * @since 25/03/2017
 */
public class LogWaitChecker implements WaitChecker {

    private final String logPattern;
    private final String containerId;
    private final Logger log;
    private final DockerAccess dockerAccess;
    private boolean first;
    private LogGetHandle logHandle;
    // Flag updated from a different thread, hence volatile (see also #595)
    private volatile AtomicBoolean detected;

    public LogWaitChecker(String pattern, DockerAccess dockerAccess, String containerId, Logger log) {
        this.log = log;
        this.dockerAccess = dockerAccess;
        this.logPattern = pattern;
        this.containerId = containerId;
        first = true;
        detected = new AtomicBoolean(false);
    }

    @Override
    public boolean check() {
        if (first) {
            final Pattern pattern = Pattern.compile(logPattern);
            log.debug("LogWaitChecker: Pattern to match '%s'", logPattern);
            logHandle = dockerAccess.getLogAsync(containerId, new LogMatchCallback(pattern));
            first = false;
        }
        return detected.get();
    }

    @Override
    public void cleanUp() {
        if (logHandle != null) {
            logHandle.finish();
        }
    }

    @Override
    public String getLogLabel() {
        return "on log out '" + logPattern + "'";
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
                logBuffer.append(txt).append("\n");
                toMatch = logBuffer.toString();
            } else {
                toMatch = txt;
            }
            if (pattern.matcher(toMatch).find()) {
                detected.set(true);
                throw new DoneException();
            }
        }

        @Override
        public void error(String error) {
            log.error("%s", error);
        }

        @Override
        public void close() {
            log.debug("Closing LogWaitChecker callback");
        }

        @Override
        public void open() {
            // no-op
        }
    }
}