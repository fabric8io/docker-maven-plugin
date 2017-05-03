package io.fabric8.maven.docker.wait;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.service.QueryService;

public class ExitCodeChecker implements WaitChecker {

    private final int exitCodeExpected;
    private final String containerId;
    private final QueryService queryService;

    public ExitCodeChecker(int exitCodeExpected, QueryService queryService, String containerId) {
        this.exitCodeExpected = exitCodeExpected;
        this.containerId = containerId;
        this.queryService = queryService;
    }

    @Override
    public boolean check() {
        try {
            Integer exitCodeActual = queryService.getMandatoryContainer(containerId).getExitCode();
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

    @Override
    public String getLogLabel() {
        return "on exit code " + exitCodeExpected;
    }
}
