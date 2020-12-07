package io.fabric8.maven.docker.wait;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.service.QueryService;
import io.fabric8.maven.docker.util.Logger;

public class ExitChecker implements WaitChecker {

    private final String containerId;
    private final QueryService queryService;

    public ExitChecker(QueryService queryService, String containerId) {
        this.containerId = containerId;
        this.queryService = queryService;
    }

    @Override
    public boolean check() {
        try {
            Integer exitCodeActual = queryService.getMandatoryContainer(containerId).getExitCode();
            // container still running
            return exitCodeActual != null;
        } catch (DockerAccessException e) {
            return true;
        }
    }

    @Override
    public void cleanUp() {
        // No cleanup required
    }

    @Override
    public String getLogLabel() {
        return "on exit code";
    }
}
