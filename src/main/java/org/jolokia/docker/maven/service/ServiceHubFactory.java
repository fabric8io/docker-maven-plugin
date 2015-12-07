package org.jolokia.docker.maven.service;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.project.MavenProject;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.assembly.DockerAssemblyManager;
import org.jolokia.docker.maven.log.LogOutputSpecFactory;
import org.jolokia.docker.maven.util.Logger;

/**
 * Factory for creating the ServiceHub (i.e. the overall context for performing all services)
 */
public class ServiceHubFactory {

    // Track started containers
    private final ContainerTracker containerTracker = new ContainerTracker();
    
    /** @component **/
    protected BuildPluginManager pluginManager;
    
    /** @component */
    protected DockerAssemblyManager dockerAssemblyManager;
    
    /** @component */
    protected MavenProject project;
    
    /** @component */
    protected MavenSession session;


    private LogOutputSpecFactory logOutputSpecFactory;

    public ServiceHub createServiceHub(DockerAccess access, Logger log, LogOutputSpecFactory logSpecFactory) {
        this.logOutputSpecFactory = logSpecFactory;
        return new ServiceHub(access, containerTracker, pluginManager, dockerAssemblyManager, project, session,
                              log, logSpecFactory);
    }

    public LogOutputSpecFactory getLogOutputSpecFactory() {
        return logOutputSpecFactory;
    }

}
