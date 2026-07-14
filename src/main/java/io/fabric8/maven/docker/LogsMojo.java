package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.log.LogDispatcher;
import io.fabric8.maven.docker.log.LogOutputSpec;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.service.QueryService;
import io.fabric8.maven.docker.service.ServiceHub;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;


/**
 * Mojo for printing logs of a container. By default the logs of all containers are shown interwoven
 * with the time occured. The log output can be highly customized in the plugin configuration, please
 * refer to the reference manual for documentation.
 *
 * This Mojo is intended for standalone usage. See {@link StartMojo} for how to enabling logging when
 * starting containers.
 *
 * @author roland
 * @since 26.03.14
 *
 */
@Mojo(name = "logs")
public class LogsMojo extends AbstractDockerMojo {

    // Whether to log infinitely or to show only the logs happened until now.
    @Parameter(property = "docker.follow", defaultValue = "false")
    private boolean follow;

    // Whether to log all containers or only the newest ones
    @Parameter(property = "docker.logAll", defaultValue = "false")
    private boolean logAll;

    @Override
    protected void executeInternal(ServiceHub hub) throws MojoExecutionException, DockerAccessException {
        QueryService queryService = hub.getQueryService();
        LogDispatcher logDispatcher = getLogDispatcher(hub);

        for (ImageConfiguration image : getResolvedImages()) {
            String imageName = image.getName();
            if (logAll) {
                for (Container container : queryService.getContainersForImage(imageName, false)) {
                    doLogging(logDispatcher, image, container.getId());
                }
            } else {
                Container container = queryService.getLatestContainerForImage(imageName, false);
                if (container != null) {
                    doLogging(logDispatcher, image, container.getId());
                }
            }
        }
        if (follow) {
            // Block forever ....
            waitForEver();
        }
    }

    private void doLogging(LogDispatcher logDispatcher, ImageConfiguration imageConfig, String container) {
        LogOutputSpec spec = serviceHubFactory.getLogOutputSpecFactory().createSpec(container, imageConfig);
        if (follow) {
            logDispatcher.trackContainerLog(container, spec);
        } else {
            logDispatcher.fetchContainerLog(container, spec);
        }
    }

    private synchronized void waitForEver() {
        // Intentionally blocks forever so that "docker:logs" keeps following the container logs until
        // the JVM is terminated; the log lines are delivered from other (dispatcher) threads. There is
        // deliberately no end condition and interrupts must not stop the follow, so both SonarCloud
        // findings on this loop (S2189 / S2142) are suppressed rather than "fixed" by changing behaviour.
        while (true) { // NOSONAR - deliberate: follow logs until the process is killed
            try {
                this.wait();
            } catch (InterruptedException e) { // NOSONAR - keep following logs; do not stop on interrupt
                // wait again
            }
        }
    }
}
