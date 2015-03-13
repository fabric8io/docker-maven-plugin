package org.jolokia.docker.maven;

import mockit.*;
import org.apache.maven.plugin.MojoExecutionException;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.util.Logger;
import org.junit.Test;

import static org.junit.Assert.*;

public class ShutdownActionTest {

    private static final int SHUTDOWN_WAIT = 500;

    @Mocked
    DockerAccess docker;

    @Mocked
    Logger log;

    private String container = "testContainer";

    @Test
    public void applies() throws Exception {
        ShutdownAction action = new ShutdownAction(createImageConfig(0),container);
        assertTrue(action.applies(null));
        assertTrue(action.applies("testName"));
        assertFalse(action.applies("anotherName"));
    }

    @Test
    public void shutdownWithoutKeepingContainers() throws Exception {
        ShutdownAction action = new ShutdownAction(createImageConfig(SHUTDOWN_WAIT),container);

        new Expectations() {{
            docker.stopContainer(container);
            log.debug(anyString); minTimes = 1;
            docker.removeContainer(container, false);
            log.info(with(getLogArgCheck(container, true)));
        }};

        long start = System.currentTimeMillis();
        action.shutdown(docker,log,false,false);
        assertTrue("Waited for at least " + SHUTDOWN_WAIT + " ms",
                   System.currentTimeMillis() - start >= SHUTDOWN_WAIT);
    }

    @Test
    public void shutdownWithoutKeepingContainersAndRemovingVolumes() throws Exception {
        ShutdownAction action = new ShutdownAction(createImageConfig(SHUTDOWN_WAIT),container);

        new Expectations() {{
            docker.stopContainer(container);
            log.debug(anyString); minTimes = 1;
            docker.removeContainer(container, true);
            log.info(with(getLogArgCheck(container, true)));
        }};

        long start = System.currentTimeMillis();
        action.shutdown(docker,log,false,true);
        assertTrue("Waited for at least " + SHUTDOWN_WAIT + " ms",
                   System.currentTimeMillis() - start >= SHUTDOWN_WAIT);
    }

    @Test
    public void shutdownWithKeepingContainer() throws Exception {
        ShutdownAction action = new ShutdownAction(createImageConfig(SHUTDOWN_WAIT),container);

        new Expectations() {{
            docker.stopContainer(container);
            log.info(with(getLogArgCheck(container, false)));
        }};
        long start = System.currentTimeMillis();
        action.shutdown(docker,log,true,false);
        assertTrue("No wait",
                   System.currentTimeMillis() - start < SHUTDOWN_WAIT);

    }

    @Test
    public void testWithoutWait() throws Exception {
        ShutdownAction action = new ShutdownAction(createImageConfig(0),container);

        new Expectations() {{
            docker.stopContainer(container);
            log.debug(anyString); times = 0;
            docker.removeContainer(container, false);
            log.info(with(getLogArgCheck(container, true)));
        }};

        long start = System.currentTimeMillis();
        action.shutdown(docker,log,false,false);
        assertTrue("No wait", System.currentTimeMillis() - start < SHUTDOWN_WAIT);

    }

    @Test(expected = MojoExecutionException.class)
    public void testWithException() throws Exception {
        ShutdownAction action = new ShutdownAction(createImageConfig(SHUTDOWN_WAIT),container);

        new Expectations() {{
            docker.stopContainer(container); result = new DockerAccessException("Test");
        }};

        action.shutdown(docker,log,false,false);
    }

    private Delegate<String> getLogArgCheck(final String container, final boolean withRemove) {
        return new Delegate<String>() {
            boolean checkArg(String txt) {
                assertTrue(txt.toLowerCase().contains("stopped"));
                assertEquals(withRemove, txt.toLowerCase().contains("removed"));
                assertTrue("Log '" + txt + "' contains " + container,txt.contains(container.substring(0,12)));
                return true;
            }
        };
    }

    private ImageConfiguration createImageConfig(int wait) {
        return new ImageConfiguration.Builder()
                .name("testName")
                .alias("testAlias")
                .runConfig(new RunImageConfiguration.Builder()
                                   .wait(new WaitConfiguration.Builder()
                                                 .shutdown(wait)
                                                 .build())
                                   .build())
                .build();
    }
}