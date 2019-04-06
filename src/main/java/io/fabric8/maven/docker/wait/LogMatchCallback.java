package io.fabric8.maven.docker.wait;

import java.util.regex.Pattern;

import io.fabric8.maven.docker.access.log.LogCallback;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.Timestamp;

class LogMatchCallback implements LogCallback {

    private final Logger logger;
    private final LogWaitCheckerCallback callback;
    private final Pattern pattern;
    private StringBuilder logBuffer;

    LogMatchCallback(final Logger logger, final LogWaitCheckerCallback callback, final String patternString) {
        this.logger = logger;
        this.callback = callback;
        this.pattern = Pattern.compile(patternString);
        logBuffer = (pattern.flags() & Pattern.DOTALL) != 0 ? new StringBuilder() : null;
    }

    @Override
    public void log(int type, Timestamp timestamp, String txt) throws DoneException {
        logger.debug("LogWaitChecker: Trying to match '%s' [Pattern: %s] [thread: %d]",
                  txt, pattern.pattern(), Thread.currentThread().getId());

        final String toMatch;
        if (logBuffer != null) {
            logBuffer.append(txt).append("\n");
            toMatch = logBuffer.toString();
        } else {
            toMatch = txt;
        }

        if (pattern.matcher(toMatch).find()) {
            logger.debug("Found log-wait pattern in log output");
            callback.matched();
            throw new DoneException();
        }
    }

    @Override
    public void error(String error) {
        logger.error("%s", error);
    }

    @Override
    public void close() {
        logger.debug("Closing LogWaitChecker callback");
    }

    @Override
    public void open() {
        logger.debug("Open LogWaitChecker callback");
    }
}
