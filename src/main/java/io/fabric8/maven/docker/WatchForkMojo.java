package io.fabric8.maven.docker;

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Watch Mojo that forks the maven lifecycle
 */
@Mojo(name = "watch-fork")
@Execute(phase = LifecyclePhase.INITIALIZE)
public class WatchForkMojo extends WatchMojo {
}
