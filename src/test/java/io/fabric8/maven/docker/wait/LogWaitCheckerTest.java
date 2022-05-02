package io.fabric8.maven.docker.wait;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.log.LogCallback;
import io.fabric8.maven.docker.access.log.LogGetHandle;
import io.fabric8.maven.docker.util.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author roland
 * @since 25/03/2017
 */
@ExtendWith(MockitoExtension.class)
class LogWaitCheckerTest {

    @Mock
    private Logger logger;

    @Mock
    private DockerAccess access;

    @Mock
    private LogGetHandle handle;

    private LogWaitChecker logWaitChecker;

    @BeforeEach
    void setup() {
        presetExpections();
        logWaitChecker = new LogWaitChecker("Hello, world!", access, "1", logger);
    }

    @Test
    void checkerRegistersAsyncLogWhenCreated() {
        Mockito.verify(access).getLogAsync(Mockito.anyString(), Mockito.any(LogCallback.class));
    }

    @Test
    void checkingAfterMatchingSucceeds() {

        logWaitChecker.matched();

        Assertions.assertTrue(logWaitChecker.check());
    }

    @Test
    void checkingWithoutMatchingFails() {

        Assertions.assertFalse(logWaitChecker.check());
    }

    @Test
    void checkerClosesLogHandle() {
        logWaitChecker.cleanUp();
        Mockito.verify(handle).finish();
    }

    @Test
    void checkerReturnsValidLogLabel() {
        final String expectedLogLabel = "on log out '" + "Hello, world!" + "'";
        Assertions.assertEquals(expectedLogLabel, logWaitChecker.getLogLabel());
    }

    private void presetExpections() {
Mockito.when(access.getLogAsync(Mockito.anyString(), Mockito.any(LogCallback.class))).thenReturn(handle);
    }
}
