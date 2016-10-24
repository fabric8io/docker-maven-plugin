package io.fabric8.maven.docker;

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Stop Mojo that forks the maven lifecycle
 */
@Mojo(name = "stop-fork", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
@Execute(phase = LifecyclePhase.INITIALIZE)
public class StopForkMojo extends StopMojo {
}
