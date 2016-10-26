package io.fabric8.maven.docker;

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Start Mojo that forks the maven lifecycle
 */
@Mojo(name = "start-fork", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
@Execute(phase = LifecyclePhase.INITIALIZE)
public class StartForkMojo extends StartMojo {
}
