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

    // Services managed by this hub, requiring
    private MojoExecutionService mojoExecutionService;
    private QueryService queryService;
    private RunService runService;
    private LogOutputSpecFactory logOutputSpecFactory;
    private BuildService buildService;
    private ArchiveService archiveService;

    // initialization flags preventing multiple initializations
    private boolean initDockerAccess = false;
    private boolean initBase = false;

    // logger to use
    private Logger logger;

    /**
     * Lifecycle method called early in order to build up the services. It can be called multiple
     * times but will run only once.
     *
     * @param log logger to use
     * @param logOutputSpecFactory factory for how to do logging
     */
    public synchronized void init(Logger log, LogOutputSpecFactory logOutputSpecFactory) {
        if (!initBase) {
            this.logOutputSpecFactory = logOutputSpecFactory;
            this.logger = log;
            mojoExecutionService = new MojoExecutionService(project, session, pluginManager);
            archiveService = new ArchiveService(dockerAssemblyManager, logger);
            initBase = true;
        }
    }

    public synchronized void initDockerAccess(DockerAccess dockerAccess) {
        if (!initDockerAccess) {
            checkBaseInitialization();
            queryService = new QueryService(dockerAccess, logger);
            runService = new RunService(dockerAccess, queryService, containerTracker, logOutputSpecFactory, logger);
            buildService = new BuildService(dockerAccess, queryService, archiveService, logger);
            initDockerAccess = true;
        }
    }

    /**
     * Get a service for executing goals on other Maven mojos
     *
     * @return service for calling other mojos
     */
    public MojoExecutionService getMojoExecutionService() {
        checkBaseInitialization();
        return mojoExecutionService;
    }

    /**
     * Service for doing the build against a Docker daemon
     *
     * @return get the build service
     */
    public BuildService getBuildService() {
        checkDockerAccessInitialization();
        return buildService;
    }


    /**
     * Get the query service for obtaining information about containers and images
     *
     * @return query service
     */
    public QueryService getQueryService() {
        checkDockerAccessInitialization();
        return queryService;
    }

    /**
     * The run service is responsible for creating and starting up containers
     *
     * @return the run service
     */
    public RunService getRunService() {
        checkDockerAccessInitialization();
        return runService;
    }


    public ArchiveService getArchiveService() {
        checkBaseInitialization();
        return archiveService;
    }

    /**
     * Get the specification for how to do logging
     *
     * @return log specification
     */
    public LogOutputSpecFactory getLogOutputSpecFactory() {
        checkBaseInitialization();
        return logOutputSpecFactory;
    }

    // ===========================================================

    private synchronized void checkDockerAccessInitialization() {
        if (!initDockerAccess) {
            throw new IllegalStateException("Service hub not yet initialized with docker access");
        }
    }

    private void checkBaseInitialization() {
        if (!initBase) {
            throw new IllegalStateException("Service hub not yet initialized");
        }
    }
}
