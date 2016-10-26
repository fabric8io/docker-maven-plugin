package io.fabric8.maven.docker;

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Logs Mojo that forks the maven lifecycle
 */
@Mojo(name = "logs-fork")
@Execute(phase = LifecyclePhase.INITIALIZE)
public class LogsForkMojo extends LogsMojo {
}
