package org.jolokia.docker.maven;

import java.io.FileNotFoundException;

import org.apache.maven.plugin.MojoExecutionException;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.log.LogOutputSpec;
import org.jolokia.docker.maven.log.LogDispatcher;
import org.jolokia.docker.maven.model.Container;
import org.jolokia.docker.maven.service.QueryService;
import org.jolokia.docker.maven.service.ServiceHub;


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
 * @goal logs
 */
public class LogsMojo extends AbstractDockerMojo {

    /**
     * Whether to log infinitely or to show only the logs happened until now.
     *
     * @parameter property = "docker.follow" default-value = "false"
     */
    private boolean follow;

    // Whether to log all containers or only the newest ones
    /** @parameter property = "docker.logAll" default-value = "false" */
    private boolean logAll;

    @Override
    protected void executeInternal(ServiceHub hub) throws MojoExecutionException, DockerAccessException {
        QueryService queryService = hub.getQueryService();
        LogDispatcher logDispatcher = getLogDispatcher(hub);

        for (ImageConfiguration image : getImages()) {
            String imageName = image.getName();
            if (logAll) {
                for (Container container : queryService.getContainersForImage(imageName)) {
                    doLogging(logDispatcher, image, container.getId());
                }
            } else {
                Container container = queryService.getLatestContainerForImage(imageName);
                doLogging(logDispatcher, image, container.getId());
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
