package io.fabric8.maven.docker;

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Remove Mojo that forks the maven lifecycle
 */
@Mojo(name = "remove-fork", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
@Execute(phase = LifecyclePhase.INITIALIZE)
public class RemoveForkMojo extends RemoveMojo {
}
