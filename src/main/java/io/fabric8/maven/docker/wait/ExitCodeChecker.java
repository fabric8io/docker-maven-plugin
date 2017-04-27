package io.fabric8.maven.docker.wait;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.service.ServiceHub;

public class ExitCodeChecker implements WaitChecker {

    private final int exitCode;
    private final ServiceHub hub;
    private final String containerId;

    public ExitCodeChecker(int exitCode, ServiceHub hub, String containerId) {
        this.exitCode = exitCode;
        this.hub = hub;
        this.containerId = containerId;
    }

    @Override
    public boolean check() {
        try {
            Integer exitCode = hub.getQueryService().getMandatoryContainer(containerId).getExitCode();
            if (exitCode == null) {
                return false;
            }
            return exitCode == this.exitCode;
        } catch (DockerAccessException e) {
            return false;
        }
    }

    @Override
    public void cleanUp() {
        // No cleanup required
    }
}
