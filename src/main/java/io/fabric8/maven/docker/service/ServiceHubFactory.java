package io.fabric8.maven.docker.service;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.project.MavenProject;
import io.fabric8.maven.docker.log.LogOutputSpecFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Factory for creating the ServiceHub (i.e. the overall context for performing all services)
 */
@Singleton
@Named
public class ServiceHubFactory {

    // Track started containers
    private final ContainerTracker containerTracker = new ContainerTracker();

    @Inject
    protected BuildPluginManager pluginManager;

    @Inject
    protected DockerAssemblyManager dockerAssemblyManager;

    private LogOutputSpecFactory logOutputSpecFactory;

    public ServiceHub createServiceHub(MavenProject project, MavenSession session, DockerAccess access, Logger log, LogOutputSpecFactory logSpecFactory) {
        this.logOutputSpecFactory = logSpecFactory;
        return new ServiceHub(access, containerTracker, pluginManager, dockerAssemblyManager, project, session,
                              log, logSpecFactory);
    }

    public LogOutputSpecFactory getLogOutputSpecFactory() {
        return logOutputSpecFactory;
    }

}
