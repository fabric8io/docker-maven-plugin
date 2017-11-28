package io.fabric8.maven.docker.wait;

import io.fabric8.maven.docker.access.log.LogCallback;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.Timestamp;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.Test;

public class LogMatchCallbackTest {

    private final String defaultPattern = "Hello, world!";

    @Mocked
    private Logger logger;

    @Mocked
    private LogWaitCheckerCallback callback;

    @Test(expected = LogCallback.DoneException.class)
    public void matchingSingleLineSucceeds() throws Exception {
        final String patternString = "The start has finished right now";
        final LogMatchCallback logMatchCallback = new LogMatchCallback(logger, callback, patternString);

        new Expectations() {{
            callback.matched();
            times = 1;
        }};

        logMatchCallback.log(1, new Timestamp(), patternString);
    }

    @Test(expected = LogCallback.DoneException.class)
    public void matchingMultipleLinesSucceeds() throws Exception {
        final String patterString = "(?s)ready to accept connections.*\\n.*ready to accept connections";
        final LogMatchCallback logMatchCallback = new LogMatchCallback(logger, callback, patterString);

        new Expectations() {{
            callback.matched();
            times = 1;
        }};

        logMatchCallback.log(1, new Timestamp(), "LOG:  database system is ready to accept connections" );
        logMatchCallback.log(1, new Timestamp(), "LOG:  autovacuum launcher started");
        logMatchCallback.log(1, new Timestamp(), "LOG:  database system is shut down");
        logMatchCallback.log(1, new Timestamp(), "LOG:  database system is ready to accept connections");
    }

    @Test
    public void matchingLinesNonConformantToThePatternFails() throws Exception {
        final String patterString = "The start has started right now";
        final LogMatchCallback logMatchCallback = new LogMatchCallback(logger, callback, patterString);

        new Expectations() {{
            callback.matched();
            times = 0;
        }};

        logMatchCallback.log(1, new Timestamp(), "LOG:  database system is ready to accept connections" );
    }

    @Test(expected = LogCallback.DoneException.class)
    public void matchingPartitialLineSucceeds() throws Exception {
        final String patterString = "waiting for connections";
        final LogMatchCallback logMatchCallback = new LogMatchCallback(logger, callback, patterString);

        new Expectations() {{
            callback.matched();
            times = 1;
        }};

        logMatchCallback.log(1, new Timestamp(), "2017-11-21T12:44:43.678+0000 I NETWORK  [initandlisten] waiting for connections on port 27017" );
    }

    @Test
    public void errorMethodProducesLogMessage() {
        final LogMatchCallback logMatchCallback = new LogMatchCallback(logger, callback, defaultPattern);

        new Expectations() {{
            logger.error(anyString, anyString);
            times = 1;
        }};

        logMatchCallback.error("The message");
    }

    @Test
    public void openMethodProducesLogMessage() {
        final LogMatchCallback logMatchCallback = new LogMatchCallback(logger, callback, "");

        new Expectations() {{
            logger.debug(anyString);
            times = 1;
        }};

        logMatchCallback.open();
    }

    @Test
    public void closeMethodProducesLogMessage() {
        final LogMatchCallback logMatchCallback = new LogMatchCallback(logger, callback, "");

        new Expectations() {{
            logger.debug(anyString);
            times = 1;
        }};

        logMatchCallback.close();
    }
}