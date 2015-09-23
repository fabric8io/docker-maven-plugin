package org.jolokia.docker.maven.service;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.assembly.InvalidAssemblerConfigurationException;
import org.apache.maven.plugin.assembly.archive.ArchiveCreationException;
import org.apache.maven.plugin.assembly.format.AssemblyFormattingException;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.assembly.AssemblyFiles;
import org.jolokia.docker.maven.assembly.DockerAssemblyManager;
import org.jolokia.docker.maven.config.BuildImageConfiguration;
import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.util.Logger;
import org.jolokia.docker.maven.util.MojoParameters;

public class BuildService {

    private final DockerAccess docker;
    private final QueryService queryService;
    private final Logger log;

    private DockerAssemblyManager dockerAssemblyManager;

    BuildService(DockerAccess docker, QueryService queryService, DockerAssemblyManager dockerAssemblyManager, Logger log) {
        this.docker = docker;
        this.queryService = queryService;
        this.dockerAssemblyManager = dockerAssemblyManager;
        this.log = log;
    }

    /**
     * Build an image
     * 
     * @param imageConfig the image configuration
     * @param params mojo params for the project
     * @throws DockerAccessException
     * @throws MojoExecutionException
     */
    public void buildImage(ImageConfiguration imageConfig, MojoParameters params)
        throws DockerAccessException, MojoExecutionException {

        String imageName = imageConfig.getName();
        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();

        String oldImageId = null;

        if (buildConfig.cleanup()) {
            oldImageId = queryService.getImageId(imageName);
        }

        // auto is now supported by docker, consider switching?
        String newImageId = buildImage(imageName, buildConfig, params);
        log.info(imageConfig.getDescription() + ": Built image " + newImageId);

        if (oldImageShouldBeRemoved(oldImageId, newImageId)) {
            docker.removeImage(oldImageId);
            log.info(imageConfig.getDescription() + ": Removed image " + oldImageId);
        }
    }

    public AssemblyFiles getAssemblyFiles(ImageConfiguration imageConfig, MojoParameters mojoParameters)
        throws MojoExecutionException {

        String name = imageConfig.getName();
        try {
            return dockerAssemblyManager.getAssemblyFiles(name, imageConfig.getBuildConfiguration(), mojoParameters, log);
        } catch (InvalidAssemblerConfigurationException | ArchiveCreationException | AssemblyFormattingException e) {
            throw new MojoExecutionException("Cannot extract assembly files for image " + name + ": " + e, e);
        }
    }

    private String buildImage(String imageName, BuildImageConfiguration buildConfig, MojoParameters mojoParameters)
        throws DockerAccessException, MojoExecutionException {

        File dockerArchive = createArchive(imageName, buildConfig, mojoParameters);
        docker.buildImage(imageName, dockerArchive, buildConfig.cleanup());
        return queryService.getImageId(imageName);
    }

    private File createArchive(String imageName, BuildImageConfiguration buildConfig, MojoParameters params) throws MojoExecutionException {
        return dockerAssemblyManager.createDockerTarArchive(imageName, params, buildConfig);
    }

    private boolean oldImageShouldBeRemoved(String oldImageId, String newImageId) {
        return oldImageId != null && !oldImageId.equals(newImageId);
    }
}
