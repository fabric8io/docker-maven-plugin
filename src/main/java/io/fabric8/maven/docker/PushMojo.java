package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.service.ServiceHub;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Goal for pushing a data-docker container
 *
 * @author roland
 */
@Mojo(name = "push", defaultPhase = LifecyclePhase.DEPLOY)
public class PushMojo extends AbstractDockerMojo {

    // Registry to use for push operations if no registry is specified
    @Parameter
    private String pushRegistry;

    @Parameter
    private boolean skipPush;
    
    /** 
     * Skip building tags
     */
    @Parameter
    private boolean skipTag;
    
    @Parameter
    private int retries;

    private String getPushRegistry() {
        return getProperty("push.registry");
    }

    private boolean getSkipPush() {
        return Boolean.parseBoolean(getProperty("skip.push"));
    }

    private boolean getSkipTag() {
        return Boolean.parseBoolean(getProperty("skip.tag"));
    }

    private int getRetries() {
        return Integer.parseInt(getProperty("push.retries", "0"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        if (getSkipPush()) {
            return;
        }

        hub.getRegistryService().pushImages(getResolvedImages(), getRetries(), getRegistryConfig(getPushRegistry()), getSkipTag());
    }

    @Override
    public String getPrefix() {
        return "docker.";
    }
}
