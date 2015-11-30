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
    public final ArchiveService archiveService;
    private final Logger log;

    BuildService(DockerAccess docker, QueryService queryService, ArchiveService archiveService, Logger log) {
        this.docker = docker;
        this.queryService = queryService;
        this.archiveService = archiveService;
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

        File dockerArchive = archiveService.createArchive(imageName, buildConfig, params);
        // auto is now supported by docker, consider switching?
        String newImageId = doBuildImage(imageName, dockerArchive, buildConfig.cleanup());
        log.info(imageConfig.getDescription() + ": Built image " + newImageId);

        if (oldImageShouldBeRemoved(oldImageId, newImageId)) {
            docker.removeImage(oldImageId, true);
            log.info(imageConfig.getDescription() + ": Removed image " + oldImageId);
        }
    }

    // ===============================================================

    private String doBuildImage(String imageName, File dockerArchive, boolean cleanUp)
        throws DockerAccessException, MojoExecutionException {
        docker.buildImage(imageName, dockerArchive, cleanUp);
        return queryService.getImageId(imageName);
    }

    private boolean oldImageShouldBeRemoved(String oldImageId, String newImageId) {
        return oldImageId != null && !oldImageId.equals(newImageId);
    }
}
