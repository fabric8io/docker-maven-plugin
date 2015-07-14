package org.jolokia.docker.maven.service;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.project.MavenProject;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.util.Logger;

public class ServiceFactory {

    private static final DependencyTracker tracker = new DependencyTracker();
    
    /** @component **/
    protected BuildPluginManager pluginManager;
    
    /** @component */
    protected MavenProject project;
    
    /** @component */
    protected MavenSession session;

    private MojoExecutionService mojoExecutionService;

    private QueryService queryService;

    private RunService runService;

    public synchronized MojoExecutionService getMojoExecutionService() {
        if (mojoExecutionService == null) {
            mojoExecutionService = new MojoExecutionService(project, session, pluginManager);
        }
        
        return mojoExecutionService;
    }

    public synchronized QueryService getQueryService(DockerAccess docker, Logger log) {
        if (queryService == null) {
            queryService = new QueryService(docker, log);
        }
        
        return queryService;
    }

    public synchronized RunService getRunService(DockerAccess docker, Logger log) {
        if (runService == null) {
            runService = new RunService(docker, getQueryService(docker, log), tracker, log);
        }
        
        return runService;
    }
}
