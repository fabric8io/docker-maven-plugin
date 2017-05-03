package io.fabric8.maven.docker.wait;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.log.LogCallback;
import io.fabric8.maven.docker.access.log.LogGetHandle;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.Timestamp;
import mockit.*;
import mockit.integration.junit4.JMockit;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.*;

/**
 * @author roland
 * @since 25/03/2017
 */
@RunWith(JMockit.class)
public class LogWaitCheckerTest {

    @Mocked
    Logger log;

    private static String CONTAINER_ID = "1234";


    @Test
    public void simple() {

        DockerAccess access = prepareDockerAccess(true, "The start has finished right now");
        LogWaitChecker wait =
            new LogWaitChecker("start.*finished", access, CONTAINER_ID, log);
        assertThat(wait.check()).isTrue();
        wait.cleanUp();
    }

    @Test
    public void simpleNegative() {

        DockerAccess access = prepareDockerAccess(false, "The start has started right now");
        LogWaitChecker wait =
            new LogWaitChecker("start.*finished", access, CONTAINER_ID, log);
        assertThat(wait.check()).isFalse();
        wait.cleanUp();
    }

    @Test
    public void multiLine() {
        DockerAccess access = prepareDockerAccess(true,
                                                  "LOG:  database system is ready to accept connections",
                                                  "LOG:  autovacuum launcher started",
                                                  "LOG:  database system is shut down",
                                                  "LOG:  database system is ready to accept connections");
        LogWaitChecker wait =
            new LogWaitChecker("(?s)ready to accept connections.*\\n.*ready to accept connections", access, CONTAINER_ID, log);
        assertThat(wait.check()).isTrue();
        wait.cleanUp();
    }

    private DockerAccess prepareDockerAccess(final boolean success, final String ... logLines) {
        return new MockUp<DockerAccess>() {
                @Mock
                LogGetHandle getLogAsync(String containerId, LogCallback callback) throws LogCallback.DoneException {
                    assertThat(containerId).isEqualTo(CONTAINER_ID);
                    try {
                        for (String logTxt : logLines) {
                            callback.log(1, new Timestamp(), logTxt);
                        }
                        if (success) {
                            fail("Should have matched");
                        }
                    } catch (LogCallback.DoneException exp) {
                        if (!success) {
                            fail("No 'done' expected");
                        }
                    }
                    return null;
                }
            }.getMockInstance();
    }

}
