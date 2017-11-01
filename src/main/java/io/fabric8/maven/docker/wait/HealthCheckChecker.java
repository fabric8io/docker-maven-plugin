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
                log.debug("HealthWaitChecker: Container %s not found");
                return false;
            }

            if (container.getHealthcheck() == null) {
                throw new IllegalArgumentException("Can not wait for healthstate of " + imageConfigDesc +". No HEALTHCHECK configured.");
            }

            if (first) {
                log.info("%s: Waiting to become healthy", imageConfigDesc);
                log.debug("HealthWaitChecker: Waiting for healthcheck: '%s'", container.getHealthcheck());
                first = false;
            } else if (log.isDebugEnabled()) {
                log.debug("HealthWaitChecker: Waiting on healthcheck '%s'", container.getHealthcheck());
            }

            return container.isHealthy();
        } catch(DockerAccessException e) {
            log.warn("Error while checking health: %s", e.getMessage());
            return false;
        }
    }

    @Override
    public void cleanUp() {}

    @Override
    public String getLogLabel() {
        try {
            final InspectedContainer container = docker.getContainer(containerId);
            return String.format("on healthcheck '%s'",container != null ? container.getHealthcheck() : "[container not found]");
        } catch (DockerAccessException e) {
            return String.format("on healthcheck [error fetching container: %s]", e.getMessage());
        }
    }
}
