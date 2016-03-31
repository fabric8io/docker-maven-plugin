package io.fabric8.maven.docker.service;

import java.io.File;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.CleanupMode;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.plugin.MojoExecutionException;

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
     * @param noCache if not null, dictate the caching behaviour. Otherwise its taken from the build configuration
     * @throws DockerAccessException
     * @throws MojoExecutionException
     */
    public void buildImage(ImageConfiguration imageConfig, MojoParameters params, boolean noCache)
        throws DockerAccessException, MojoExecutionException {

        String imageName = imageConfig.getName();
        ImageName.validate(imageName);

        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();

        String oldImageId = null;

        CleanupMode cleanupMode = buildConfig.cleanupMode();
        if (cleanupMode.isRemove()) {
            oldImageId = queryService.getImageId(imageName);
        }

        File dockerArchive = archiveService.createArchive(imageName, buildConfig, params);
        // auto is now supported by docker, consider switching?
        String newImageId =
                doBuildImage(imageName, dockerArchive,
                             cleanupMode.isRemove(),
                             noCache);
        log.info(imageConfig.getDescription() + ": Built image " + newImageId);

        if (oldImageId != null && !oldImageId.equals(newImageId)) {
            try {
                docker.removeImage(oldImageId, true);
                log.info(imageConfig.getDescription() + ": Removed image " + oldImageId);
            } catch (DockerAccessException exp) {
                if (cleanupMode == CleanupMode.TRY_TO_REMOVE) {
                    log.warn(imageConfig.getDescription() +": " + exp.getMessage() + " (old image)" +
                             (exp.getCause() != null ? " [" + exp.getCause().getMessage() + "]" : ""));
                } else {
                    throw exp;
                }
            }
        }
    }

    // ===============================================================

    private String doBuildImage(String imageName, File dockerArchive, boolean cleanUp, boolean noCache)
        throws DockerAccessException, MojoExecutionException {
        docker.buildImage(imageName, dockerArchive, cleanUp, noCache);
        return queryService.getImageId(imageName);
    }

}
