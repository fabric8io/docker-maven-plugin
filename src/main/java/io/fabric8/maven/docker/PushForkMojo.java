package io.fabric8.maven.docker;

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Push Mojo that forks the maven lifecycle
 */
@Mojo(name = "push-fork", defaultPhase = LifecyclePhase.DEPLOY)
@Execute(phase = LifecyclePhase.INITIALIZE)
public class PushForkMojo extends PushMojo {
}
