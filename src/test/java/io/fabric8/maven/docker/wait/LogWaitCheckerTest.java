package io.fabric8.maven.docker.wait;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.log.LogCallback;
import io.fabric8.maven.docker.access.log.LogGetHandle;
import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author roland
 * @since 25/03/2017
 */

public class LogWaitCheckerTest {

    @Mocked
    private Logger logger;

    @Mocked
    private DockerAccess access;

    @Mocked
    private LogGetHandle handle;

    private LogWaitChecker logWaitChecker;

    @Before
    public void setup() {

        presetExpections();
        logWaitChecker = new LogWaitChecker("Hello, world!", access, "1", logger);
    }

    @Test
    public void checkerRegistersAsyncLogWhenCreated() {

        new Expectations() {{
            access.getLogAsync(anyString, withInstanceOf(LogCallback.class));
            times = 1;
        }};

        final LogWaitChecker logWaitChecker =
                new LogWaitChecker("Hello, world!", access, "1", logger);
    }

    @Test
    public void checkingAfterMatchingSucceeds() {

        logWaitChecker.matched();

        assertThat(logWaitChecker.check()).isTrue();
    }

    @Test
    public void checkingWithoutMatchingFails() {

        assertThat(logWaitChecker.check()).isFalse();
    }

    @Test
    public void checkerClosesLogHandle() {

        new Expectations() {{
            handle.finish();
            times = 1;
        }};

        logWaitChecker.cleanUp();
    }

    @Test
    public void checkerReturnsValidLogLabel() {

        final String expectedLogLabel = "on log out '" + "Hello, world!" + "'";
        assertThat(logWaitChecker.getLogLabel()).isEqualTo(expectedLogLabel);
    }

    private void presetExpections() {

        new Expectations() {{
            access.getLogAsync(anyString, withInstanceOf(LogCallback.class));
            times = 1;
            result = handle;
        }};
    }
}
