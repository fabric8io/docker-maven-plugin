package io.fabric8.maven.docker.wait;

import java.util.concurrent.CountDownLatch;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.log.LogGetHandle;
import io.fabric8.maven.docker.util.Logger;

/**
 * @author roland
 * @since 25/03/2017
 */
public class LogWaitChecker implements WaitChecker, LogWaitCheckerCallback {

    private final String containerId;
    private final String logPattern;
    private final Logger log;

    private final CountDownLatch latch;
    private final LogGetHandle logHandle;

    public LogWaitChecker(final String logPattern, final DockerAccess dockerAccess, final String containerId, final Logger log) {
        this.containerId = containerId;
        this.logPattern = logPattern;
        this.log = log;

        this.latch = new CountDownLatch(1);
        this.logHandle = dockerAccess.getLogAsync(containerId, new LogMatchCallback(log, this, logPattern));
    }

    @Override
    public void matched() {
        latch.countDown();
        log.info("Pattern '%s' matched for container %s", logPattern, containerId);
    }

    @Override
    public boolean check() {
        return latch.getCount() == 0;
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
}