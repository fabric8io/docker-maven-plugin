package org.jolokia.docker.maven.service;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.assembly.DockerAssemblyManager;
import org.jolokia.docker.maven.log.LogOutputSpecFactory;
import org.jolokia.docker.maven.util.Logger;

/**
 * Factory for creating the ServiceHub (i.e. the overall context for performing all services)
 */
@Component(role = ServiceHubFactory.class, instantiationStrategy = "singleton")
public class ServiceHubFactory {

    // Track started containers
    private final ContainerTracker containerTracker = new ContainerTracker();
    
    @Requirement
    protected BuildPluginManager pluginManager;

    @Requirement
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
