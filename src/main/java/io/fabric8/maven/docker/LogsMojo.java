package io.fabric8.maven.docker;

import java.io.FileNotFoundException;

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
                for (Container container : queryService.getContainersForImage(imageName)) {
                    doLogging(logDispatcher, image, container.getId());
                }
            } else {
                Container container = queryService.getLatestContainerForImage(imageName);
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

    private void doLogging(LogDispatcher logDispatcher, ImageConfiguration imageConfig, String container) throws MojoExecutionException {
        LogOutputSpec spec = serviceHubFactory.getLogOutputSpecFactory().createSpec(container, imageConfig);
        try {
            if (follow) {
                logDispatcher.trackContainerLog(container, spec);
            } else {
                logDispatcher.fetchContainerLog(container, spec);
            }
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("Can not log to file " + spec.getFile());
        }
    }

    private synchronized void waitForEver() {
        while (true) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                // sleep again
            }
        }
    }
}
