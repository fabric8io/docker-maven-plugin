package io.fabric8.maven.docker;

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Build Mojo that forks the maven lifecycle
 */
@Mojo(name = "build-fork", defaultPhase = LifecyclePhase.INSTALL)
@Execute(phase = LifecyclePhase.INITIALIZE)
public class BuildForkMojo extends BuildMojo {
}
