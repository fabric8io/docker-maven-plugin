package io.fabric8.maven.docker.service;/*
 *
 * Copyright 2015 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.log.LogOutputSpecFactory;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.project.MavenProject;

/**
 * A service hub responsible for creating and managing services which are used by
 * Mojos for calling to the docker backend. The docker backend (DAO) is injected from the outside.
 *
 * @author roland
 * @since 01/12/15
 */
public class ServiceHub {

    private final DockerAccess dockerAccess;

    private final QueryService queryService;
    private final RunService runService;
    private final BuildService buildService;
    private final MojoExecutionService mojoExecutionService;
    private final ArchiveService archiveService;
    private final VolumeService volumeService;

    ServiceHub(DockerAccess dockerAccess, ContainerTracker containerTracker, BuildPluginManager pluginManager,
               DockerAssemblyManager dockerAssemblyManager, MavenProject project, MavenSession session,
               Logger logger, LogOutputSpecFactory logSpecFactory) {

        this.dockerAccess = dockerAccess;

        mojoExecutionService = new MojoExecutionService(project, session, pluginManager);
        archiveService = new ArchiveService(dockerAssemblyManager, logger);

        if (dockerAccess != null) {
            queryService = new QueryService(dockerAccess);
            runService = new RunService(dockerAccess, queryService, containerTracker, logSpecFactory, logger);
            buildService = new BuildService(dockerAccess, queryService, archiveService, logger);
            volumeService = new VolumeService(dockerAccess);
        } else {
            queryService = null;
            runService = null;
            buildService = null;
            volumeService = null;
        }
    }

    /**
     * Get access object for contacting the docker daemon
     *
     * @return docker access object
     */
    public DockerAccess getDockerAccess() {
        checkDockerAccessInitialization();
        return dockerAccess;
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
    
    /**
     * The volume service is responsible for creating volumes
     *
     * @return the run service
     */
    public VolumeService getVolumeService() {
        checkDockerAccessInitialization();
        return volumeService;
    }

    public ArchiveService getArchiveService() {
        return archiveService;
    }

    /**
     * Get a service for executing goals on other Maven mojos
     *
     * @return service for calling other mojos
     */
    public MojoExecutionService getMojoExecutionService() {
        return mojoExecutionService;
    }

    private synchronized void checkDockerAccessInitialization() {
        if (dockerAccess == null) {
            throw new IllegalStateException("Service hub created without a docker access to a docker daemon");
        }
    }


}
