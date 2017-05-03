package io.fabric8.maven.docker.wait;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.service.ServiceHub;

public class ExitCodeChecker implements WaitChecker {

    private final int exitCodeExpected;
    private final ServiceHub hub;
    private final String containerId;

    public ExitCodeChecker(int exitCodeExpected, ServiceHub hub, String containerId) {
        this.exitCodeExpected = exitCodeExpected;
        this.hub = hub;
        this.containerId = containerId;
    }

    @Override
    public boolean check() {
        try {
            Integer exitCodeActual = hub.getQueryService().getMandatoryContainer(containerId).getExitCode();
            // container still running
            return exitCodeActual != null && exitCodeActual == exitCodeExpected;
        } catch (DockerAccessException e) {
            return false;
        }
    }

    @Override
    public void cleanUp() {
        // No cleanup required
    }
}
