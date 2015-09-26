package org.jolokia.docker.maven.service;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.project.MavenProject;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.assembly.DockerAssemblyManager;
import org.jolokia.docker.maven.log.LogOutputSpecFactory;
import org.jolokia.docker.maven.util.Logger;

/**
 * A service hub responsible for creating and managing service which are used by
 * Mojos for calling to the docker backend. The docker backend (DAO) is injected from the outside.
 */
public class ServiceHub {

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

    // Services managed by this hub
    private MojoExecutionService mojoExecutionService;
    private QueryService queryService;
    private RunService runService;
    private LogOutputSpecFactory logOutputSpecFactory;

    private BuildService buildService;
    // initialization flag preventing multiple initializations
    private boolean initDone = false;

    /**
     * Lifecycle method called early in order to build up the services. It can be called multiple
     * times but will run only once.
     *
     * @param dockerAccess the docker access object
     * @param log logger to use
     */
    public synchronized void init(DockerAccess dockerAccess, Logger log, LogOutputSpecFactory logOutputSpecFactory) {
        if (!initDone) {
            this.logOutputSpecFactory = logOutputSpecFactory;
            mojoExecutionService = new MojoExecutionService(project, session, pluginManager);
            queryService = new QueryService(dockerAccess, log);
            runService = new RunService(dockerAccess, queryService, containerTracker, logOutputSpecFactory, log);
            buildService = new BuildService(dockerAccess, queryService, dockerAssemblyManager, log);
            initDone = true;
        }
    }

    /**
     * Get a service for executing goals on other Maven mojos
     *
     * @return service for calling other mojos
     */
    public MojoExecutionService getMojoExecutionService() {
        checkInitialization();
        return mojoExecutionService;
    }

    public BuildService getBuildService() {
        checkInitialization();
        return buildService;
    }
    
    /**
     * Get the query service for obtaining information about containers and images
     *
     * @return query service
     */
    public QueryService getQueryService() {
        checkInitialization();
        return queryService;
    }

    /**
     * The run service is responsible for creating and starting up containers
     *
     * @return the run service
     */
    public RunService getRunService() {
        checkInitialization();
        return runService;
    }

    // ===========================================================

    private synchronized void checkInitialization() {
        if (!initDone) {
            throw new IllegalStateException("Service hub not yet initialized");
        }
    }

    public LogOutputSpecFactory getLogOutputSpecFactory() {
        return logOutputSpecFactory;
    }
}
