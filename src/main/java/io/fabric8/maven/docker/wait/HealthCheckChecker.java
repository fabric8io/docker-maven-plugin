package io.fabric8.maven.docker.wait;

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

    private DockerAccess docker;
    private String containerId;
    private Logger log;
    private final String imageConfigDesc;
    private String healthcheck;

    public HealthCheckChecker(DockerAccess docker, String containerId, String imageConfigDesc, Logger log) {
        this.docker = docker;
        this.containerId = containerId;
        this.imageConfigDesc = imageConfigDesc;
        this.log = log;
    }

    @Override
    public boolean check() {
        try {
            final InspectedContainer container = docker.getContainer(containerId);
            if (container == null) {
                log.debug("HealthyWaitChecker: Container %s not found");
                return false;
            }

            healthcheck = container.getHealthcheck();
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

    @Override
    public String getLogLabel() {
        return "on healthcheck '" + healthcheck + "'";
    }
}
