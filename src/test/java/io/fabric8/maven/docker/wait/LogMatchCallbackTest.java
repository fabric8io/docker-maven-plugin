package io.fabric8.maven.docker.wait;

import io.fabric8.maven.docker.access.log.LogCallback;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.TimestampFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;

@ExtendWith(MockitoExtension.class)
class LogMatchCallbackTest {

    private static final String HELLO_WORLD = "Hello, world!";

    @Mock
    private Logger logger;

    @Mock
    private LogWaitCheckerCallback callback;

    @Test
    void matchingSingleLineSucceeds() {
        final String patternString = "The start has finished right now";
        final LogMatchCallback logMatchCallback = new LogMatchCallback(logger, callback, patternString);
        
        Assertions.assertThrows(LogCallback.DoneException.class,
            ()-> logMatchCallback.log(1, TimestampFactory.createTimestamp(), patternString));
        Mockito.verify(callback).matched();
    }

    @Test
    void matchingMultipleLinesSucceeds() throws LogCallback.DoneException {
        final String patterString = "(?s)ready to accept connections.*\\n.*ready to accept connections";
        final LogMatchCallback logMatchCallback = new LogMatchCallback(logger, callback, patterString);

        logMatchCallback.log(1, TimestampFactory.createTimestamp(), "LOG:  database system is ready to accept connections" );
        logMatchCallback.log(1, TimestampFactory.createTimestamp(), "LOG:  autovacuum launcher started");
        logMatchCallback.log(1, TimestampFactory.createTimestamp(), "LOG:  database system is shut down");

        Assertions.assertThrows(LogCallback.DoneException.class,
            ()-> logMatchCallback.log(1, TimestampFactory.createTimestamp(), "LOG:  database system is ready to accept connections"));

        Mockito.verify(callback).matched();
    }

    @Test
    void matchingLinesNonConformantToThePatternFails() throws LogCallback.DoneException {
        final String patterString = "The start has started right now";
        final LogMatchCallback logMatchCallback = new LogMatchCallback(logger, callback, patterString);

        logMatchCallback.log(1, TimestampFactory.createTimestamp(), "LOG:  database system is ready to accept connections" );

        Mockito.verify(callback, Mockito.never()).matched();
    }

    @Test
    void matchingPartitialLineSucceeds() {
        final String pattern = "waiting for connections";
        final LogMatchCallback logMatchCallback = new LogMatchCallback(logger, callback, pattern);

        ZonedDateTime timestamp = TimestampFactory.createTimestamp();
        Assertions.assertThrows(LogCallback.DoneException.class, 
            ()-> logMatchCallback.log(1, timestamp, "2017-11-21T12:44:43.678+0000 I NETWORK  [initandlisten] waiting for connections on port 27017" ));

        Mockito.verify(callback).matched();
    }

    @Test
    void errorMethodProducesLogMessage() {
        final LogMatchCallback logMatchCallback = new LogMatchCallback(logger, callback, HELLO_WORLD);

        logMatchCallback.error("The message");
        Mockito.verify(logger).error(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void openMethodProducesLogMessage() {
        final LogMatchCallback logMatchCallback = new LogMatchCallback(logger, callback, "");

        logMatchCallback.open();
        Mockito.verify(logger).debug(Mockito.anyString());
    }

    @Test
    void closeMethodProducesLogMessage() {
        final LogMatchCallback logMatchCallback = new LogMatchCallback(logger, callback, "");

        logMatchCallback.close();
        Mockito.verify(logger).debug(Mockito.anyString());
    }
}