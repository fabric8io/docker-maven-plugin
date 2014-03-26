package org.jolokia.maven.docker;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author roland
 * @since 26.03.14
 */
abstract public class AbstractDockerMojo extends AbstractMojo {

    protected static final String PROPERTY_CONTAINER_ID = "docker.containerId";

    @Parameter(property = "docker.url",defaultValue = "http://localhost:4243")
    private String url;

    protected DockerAccess createDockerAccess() {
        return new DockerAccess(url.replace("^tcp://","http://"),getLog());
    }

    protected void info(String info) {
        getLog().info(info);
    }

    protected void error(String error) {
        getLog().error(error);
    }

}
