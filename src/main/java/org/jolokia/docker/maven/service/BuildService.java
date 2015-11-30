package org.jolokia.docker.maven.service;

import java.io.File;
import java.util.List;

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

        File dockerArchive = createArchive(imageName, buildConfig, params);
        // auto is now supported by docker, consider switching?
        String newImageId = doBuildImage(imageName, dockerArchive, buildConfig.cleanup());
        log.info(imageConfig.getDescription() + ": Built image " + newImageId);

        if (oldImageShouldBeRemoved(oldImageId, newImageId)) {
            docker.removeImage(oldImageId);
            log.info(imageConfig.getDescription() + ": Removed image " + oldImageId);
        }
    }

    /**
     * Create the tar file container the source for building an image. This tar can be used directly for
     * uploading to a Docker daemon for creating the image
     *
     * @param imageConfig the image configuration
     * @param params mojo params for the project
     * @return file for holding the sources
     * @throws MojoExecutionException if during creation of the tar an error occurs.
     */
    public File createDockerBuildTar(ImageConfiguration imageConfig, MojoParameters params) throws MojoExecutionException {
        File ret = createArchive(imageConfig.getName(), imageConfig.getBuildConfiguration(), params);
        log.info(imageConfig.getDescription() + ": Created docker source tar " + ret);
        return ret;
    }

    /**
     * Get a mapping of original to destination files which a covered by an assembly. This can be used
     * to watch the source files for changes in order to update the target (either by recreating a docker image
     * or by copying it into a running container)
     *
     * @param imageConfig image config for which to get files. The build- and assembly configuration in this image
     *                    config must not be null.
     * @param mojoParameters needed for tracking the assembly
     * @return mapping of assembly files
     * @throws MojoExecutionException
     */
    public AssemblyFiles getAssemblyFiles(ImageConfiguration imageConfig, MojoParameters mojoParameters)
        throws MojoExecutionException {

        String name = imageConfig.getName();
        try {
            return dockerAssemblyManager.getAssemblyFiles(name, imageConfig.getBuildConfiguration(), mojoParameters, log);
        } catch (InvalidAssemblerConfigurationException | ArchiveCreationException | AssemblyFormattingException e) {
            throw new MojoExecutionException("Cannot extract assembly files for image " + name + ": " + e, e);
        }
    }

    /**
     * Create an tar archive from a set of assembly files. Only files which changed since the last call are included.
     * @param entries changed files. List must not be empty or null
     * @param imageName image's name
     * @param mojoParameters
     * @return created archive
     */
    public File createChangedFilesArchive(List<AssemblyFiles.Entry> entries, File assemblyDir, String imageName, MojoParameters mojoParameters) throws MojoExecutionException {
        return dockerAssemblyManager.createChangedFilesArchive(entries, assemblyDir, imageName, mojoParameters);
    }

    // ===============================================================

    private String doBuildImage(String imageName, File dockerArchive, boolean cleanUp)
        throws DockerAccessException, MojoExecutionException {
        docker.buildImage(imageName, dockerArchive, cleanUp);
        return queryService.getImageId(imageName);
    }

    private File createArchive(String imageName, BuildImageConfiguration buildConfig, MojoParameters params) throws MojoExecutionException {
        return dockerAssemblyManager.createDockerTarArchive(imageName, params, buildConfig);
    }

    private boolean oldImageShouldBeRemoved(String oldImageId, String newImageId) {
        return oldImageId != null && !oldImageId.equals(newImageId);
    }
}
