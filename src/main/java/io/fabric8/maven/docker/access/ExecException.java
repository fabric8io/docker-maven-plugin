package io.fabric8.maven.docker.access;

import java.util.Arrays;

import io.fabric8.maven.docker.model.ContainerDetails;
import io.fabric8.maven.docker.model.ExecDetails;

/**
 * Exception thrown when the execution of an exec container failed
 *
 * @author roland
 * @since 18.01.18
 */
public class ExecException extends Exception {

    private final ExecDetails details;
    private final ContainerDetails container;

    public ExecException(ExecDetails execDetails, ContainerDetails containerDetails) {
        this.details = execDetails;
        this.container = containerDetails;
        if (execDetails.getExitCode() == null || execDetails.getExitCode() != 0) {
            throw new IllegalArgumentException(
                String.format(
                    "Exception while creating an exception: Exit code must be not 0 for %s %s executed on %s [%s](%s)",
                    execDetails.getEntryPoint(),
                    Arrays.toString(execDetails.getArguments()),
                    containerDetails.getName(),
                    containerDetails.getImage(),
                    containerDetails.getId()));
        }
    }

    @Override
    public String toString() {
        return String.format(
            "Executing '%s' with args '%s' inside container %s [%s](%s) resulted in a non-zero exit code %d",
            details.getEntryPoint(),
            Arrays.toString(details.getArguments()),
            container.getName(),
            container.getImage(),
            container.getId(),
            details.getExitCode());
    }
}
