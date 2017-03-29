package io.fabric8.maven.docker.wait;

import java.util.List;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.model.InspectedContainer;
import io.fabric8.maven.docker.util.Logger;

/**
 * @author roland
 * @since 29/03/2017
 */
public class HealthCheckChecker implements WaitChecker {

        private boolean first = true;

    private final List<String> logOut;

    private DockerAccess docker;
    private String containerId;
    private Logger log;
    private final String imageConfigDesc;

    public HealthCheckChecker(DockerAccess docker, String containerId, String imageConfigDesc, List<String> logOut, Logger log) {
        this.docker = docker;
        this.containerId = containerId;
        this.imageConfigDesc = imageConfigDesc;
        this.logOut = logOut;
        this.log = log;
    }

    @Override
    public boolean check() {
        try {
            final InspectedContainer container = docker.getContainer(containerId);
            if (container == null) {
                log.debug("HealthyWaitChecker:  Container %s not found");
                return false;
            }

            final String healthcheck = container.getHealthcheck();
            if (first) {
                if (healthcheck == null) {
                    throw new IllegalArgumentException("Can not wait for healthy state of " + imageConfigDesc +". No HEALTHCHECK configured.");
                }
                log.info("%s: Waiting to become healthy", imageConfigDesc);
                log.debug("HealthyWaitChecker: Waiting for healthcheck: '%s'", healthcheck);
                first = false;
            } else if (log.isDebugEnabled()) {
                log.debug("HealthyWaitChecker: Waiting on healthcheck '%s'", healthcheck);
            }

            return container.isHealthy();
        } catch(DockerAccessException e) {
            return false;
        }
    }

    @Override
    public void cleanUp() {}
};
