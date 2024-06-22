package io.fabric8.maven.docker.service;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonParseException;
import io.fabric8.maven.docker.access.BuildOptions;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.CleanupMode;
import io.fabric8.maven.docker.config.ConfigHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.model.ImageArchiveManifest;
import io.fabric8.maven.docker.model.ImageArchiveManifestEntry;
import io.fabric8.maven.docker.service.helper.BuildArgResolver;
import io.fabric8.maven.docker.util.DockerFileUtil;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageArchiveUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import io.fabric8.maven.docker.util.NamePatternUtil;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.PatternSyntaxException;

public class BuildService {
    private final DockerAccess docker;
    private final QueryService queryService;
    private final ArchiveService archiveService;
    private final RegistryService registryService;
    private final Logger log;

    BuildService(DockerAccess docker, QueryService queryService, RegistryService registryService, ArchiveService archiveService, Logger log) {
        this.docker = docker;
        this.queryService = queryService;
        this.registryService = registryService;
        this.archiveService = archiveService;
        this.log = log;
    }

    /**
     * Pull the base image if needed and run the build.
     *
     * @param imageConfig  the image configuration
     * @param buildContext the build context
     * @throws DockerAccessException
     * @throws MojoExecutionException
     */
    public void buildImage(ImageConfiguration imageConfig, ImagePullManager imagePullManager, BuildContext buildContext, File buildArchiveFile)
            throws DockerAccessException, MojoExecutionException {

        BuildArgResolver buildArgResolver = new BuildArgResolver(log);
        Map<String, String> buildArgsFromExternalSources = buildArgResolver.resolveBuildArgs(buildContext);
        if (imagePullManager != null) {
            autoPullBaseImage(imageConfig, imagePullManager, buildContext, prepareBuildArgs(buildArgsFromExternalSources, imageConfig.getBuildConfiguration()));
            autoPullCacheFromImage(imageConfig, imagePullManager, buildContext);
        }

        buildImage(imageConfig, buildContext.getMojoParameters(), ConfigHelper.isNoCache(imageConfig), checkForSquash(imageConfig), buildArgsFromExternalSources, buildArchiveFile);
    }

    /**
     * Create docker archive for building image
     *
     * @param imageConfiguration image configuration
     * @param buildContext       docker build context
     * @param archivePath        build archive only flag, it can have values TRUE or FALSE and also
     *                           it can hold path to archive where it might get copied over
     * @return tarball for docker image
     * @throws MojoExecutionException in case any exception comes during building tarball
     */
    public File buildArchive(ImageConfiguration imageConfiguration, BuildContext buildContext, String archivePath)
            throws MojoExecutionException {
        String imageName = imageConfiguration.getName();
        ImageName.validate(imageName);
        BuildImageConfiguration buildConfig = imageConfiguration.getBuildConfiguration();
        MojoParameters params = buildContext.getMojoParameters();

        if (buildConfig.getDockerArchive() != null) {
            return buildConfig.getAbsoluteDockerTarPath(params);
        }
        long time = System.currentTimeMillis();

        File dockerArchive = archiveService.createArchive(imageName, buildConfig, params, log);
        log.info("%s: Created %s in %s", imageConfiguration.getDescription(), dockerArchive.getName(), EnvUtil.formatDurationTill(time));

        // Copy created tarball to directory if specified
        try {
            copyDockerArchive(imageConfiguration, dockerArchive, archivePath);
        } catch (IOException exception) {
            throw new MojoExecutionException("Error while copying created tar to specified buildArchive path: " + archivePath,
                    exception);
        }
        return dockerArchive;
    }

    public void copyDockerArchive(ImageConfiguration imageConfiguration, File dockerArchive, String archivePath) throws IOException {
        if (archivePath != null && !archivePath.isEmpty()) {
            Files.copy(dockerArchive.toPath(), new File(archivePath, dockerArchive.getName()).toPath());
            log.info("%s: Copied created tarball to %s", imageConfiguration.getDescription(), archivePath);
        }
    }

    public void tagImage(ImageConfiguration imageConfig) throws DockerAccessException {
        List<String> tags = imageConfig.getBuildConfiguration().getTags();
        if (!tags.isEmpty()) {
            String imageName = imageConfig.getName();
            log.info("%s: Tag with %s", imageConfig.getDescription(), String.join(",", tags));

            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();

            for (String tag : tags) {
                tagImage(imageName, tag, null, buildConfig.cleanupMode());
            }
        }
    }

    /**
     * Build an image
     *
     * @param imageConfig the image configuration
     * @param params      mojo params for the project
     * @param noCache     if not null, dictate the caching behaviour. Otherwise its taken from the build configuration
     * @param buildArgs   docker build args
     * @throws DockerAccessException
     * @throws MojoExecutionException
     */
    protected void buildImage(ImageConfiguration imageConfig, MojoParameters params, boolean noCache, boolean squash, Map<String, String> buildArgs, File dockerArchive)
            throws DockerAccessException, MojoExecutionException {

        String imageName = imageConfig.getName();
        ImageName.validate(imageName);

        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();

        String oldImageId = null;

        CleanupMode cleanupMode = buildConfig.cleanupMode();
        if (cleanupMode.isRemove()) {
            oldImageId = queryService.getImageId(imageName);
        }

        if (buildConfig.getDockerArchive() != null) {
            File tarArchive = buildConfig.getAbsoluteDockerTarPath(params);
            String archiveImageName = getArchiveImageName(buildConfig, tarArchive);

            long time = System.currentTimeMillis();

            docker.loadImage(imageName, tarArchive);
            log.info("%s: Loaded tarball in %s", buildConfig.getDockerArchive(), EnvUtil.formatDurationTill(time));

            if (archiveImageName != null && !archiveImageName.equals(imageName)) {
                docker.tag(archiveImageName, imageName, true);
            }

            return;
        }

        Map<String, String> mergedBuildMap = prepareBuildArgs(buildArgs, buildConfig);

        // auto is now supported by docker, consider switching?
        BuildOptions opts =
                new BuildOptions(buildConfig.getBuildOptions())
                        .dockerfile(buildConfig.getDockerfileName())
                        .forceRemove(cleanupMode.isRemove())
                        .noCache(noCache)
                        .squash(squash)
                        .cacheFrom(buildConfig.getCacheFrom())
                        .network(buildConfig.getNetwork())
                        .buildArgs(mergedBuildMap);
        String newImageId = doBuildImage(imageName, dockerArchive, opts);
        log.info("%s: Built image %s", imageConfig.getDescription(), newImageId);

        removeDanglingImage(imageName, oldImageId, newImageId, cleanupMode, true);
    }

    public void tagImage(String imageName, String tag, String repo, CleanupMode cleanupMode) throws DockerAccessException {
        if (tag != null) {
            String fullImageName = new ImageName(imageName, tag).getNameWithOptionalRepository(repo);

            String oldImageId = null;
            if (cleanupMode.isRemove()) {
                oldImageId = queryService.getImageId(fullImageName);
            }

            docker.tag(imageName, fullImageName, true);
            log.info("Tagging image %s successful!", fullImageName);

            String newImageId = queryService.getImageId(fullImageName);

            removeDanglingImage(fullImageName, oldImageId, newImageId, cleanupMode, false);
        }
    }

    static Map<String, String> prepareBuildArgs(Map<String, String> buildArgs, BuildImageConfiguration buildConfig) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String>builder().putAll(Optional.ofNullable(buildArgs).orElse(Collections.emptyMap()));
        if (buildConfig.getArgs() != null) {
            builder.putAll(buildConfig.getArgs());
        }
        return builder.build();
    }

    private String getArchiveImageName(BuildImageConfiguration buildConfig, File tarArchive) throws MojoExecutionException {
        if (buildConfig.getLoadNamePattern() == null || buildConfig.getLoadNamePattern().length() == 0) {
            return null;
        }

        ImageArchiveManifest manifest;
        try {
            manifest = readArchiveManifest(tarArchive);
        } catch (IOException | JsonParseException e) {
            throw new MojoExecutionException("Unable to read image manifest in archive " + buildConfig.getDockerArchive(), e);
        }

        String archiveImageName;

        try {
            archiveImageName = matchArchiveImagesToPattern(buildConfig.getLoadNamePattern(), manifest);
        } catch (PatternSyntaxException e) {
            throw new MojoExecutionException("Unable to interpret loadNamePattern " + buildConfig.getLoadNamePattern(), e);
        }

        if (archiveImageName == null) {
            throw new MojoExecutionException("No image in the archive has a tag that matches pattern " + buildConfig.getLoadNamePattern());
        }

        return archiveImageName;
    }

    private ImageArchiveManifest readArchiveManifest(File tarArchive) throws IOException, JsonParseException {
        long time = System.currentTimeMillis();

        ImageArchiveManifest manifest = ImageArchiveUtil.readManifest(tarArchive);

        log.info("%s: Read archive manifest in %s", tarArchive, EnvUtil.formatDurationTill(time));

        // Show the results of reading the manifest to users trying to debug their configuration
        if (log.isDebugEnabled()) {
            for (ImageArchiveManifestEntry entry : manifest.getEntries()) {
                log.debug("Entry ID: %s has %d repo tag(s)", entry.getId(), entry.getRepoTags().size());
                for (String repoTag : entry.getRepoTags()) {
                    log.debug("Repo Tag: %s", repoTag);
                }
            }
        }

        return manifest;
    }

    private String matchArchiveImagesToPattern(String imageNamePattern, ImageArchiveManifest manifest) {
        String imageNameRegex = NamePatternUtil.convertNamePattern(imageNamePattern);
        log.debug("Image name regex is %s", imageNameRegex);

        Map<String, ImageArchiveManifestEntry> entries = ImageArchiveUtil.findEntriesByRepoTagPattern(imageNameRegex, manifest);

        // Show the matches from the manifest to users trying to debug their configuration
        if (log.isDebugEnabled()) {
            for (Map.Entry<String, ImageArchiveManifestEntry> entry : entries.entrySet()) {
                log.debug("Repo tag pattern matched %s referring to image %s", entry.getKey(), entry.getValue().getId());
            }
        }

        if (!entries.isEmpty()) {
            Map.Entry<String, ImageArchiveManifestEntry> matchedEntry = entries.entrySet().iterator().next();

            if (ImageArchiveUtil.mapEntriesById(entries.values()).size() > 1) {
                log.warn("Multiple image ids matched pattern %s: using tag %s associated with id %s",
                        imageNamePattern, matchedEntry.getKey(), matchedEntry.getValue().getId());
            } else {
                log.info("Using image tag %s from archive", matchedEntry.getKey());
            }

            return matchedEntry.getKey();
        }

        return null;
    }

    private String doBuildImage(String imageName, File dockerArchive, BuildOptions options)
            throws DockerAccessException, MojoExecutionException {
        docker.buildImage(imageName, dockerArchive, options);
        return queryService.getImageId(imageName);
    }



    private void autoPullBaseImage(ImageConfiguration imageConfig, ImagePullManager imagePullManager, BuildContext buildContext, Map<String, String> buildArgs)
            throws DockerAccessException, MojoExecutionException {
        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
        CleanupMode cleanupMode = buildConfig.cleanupMode();

        if (buildConfig.getDockerArchive() != null) {
            // No auto pull needed in archive mode
            return;
        }

        List<String> fromImages;
        if (buildConfig.isDockerFileMode()) {
            fromImages = extractBaseFromDockerfile(buildConfig, buildContext.getMojoParameters(), buildArgs);
        } else {
            fromImages = new LinkedList<>();
            String baseImage = extractBaseFromConfiguration(buildConfig);
            if (baseImage != null) {
                fromImages.add(baseImage);
            }
        }
        for (String fromImage : fromImages) {
            if (fromImage != null && !DockerAssemblyManager.SCRATCH_IMAGE.equals(fromImage)) {
                String oldImageId = null;
                if (cleanupMode.isRemove()) {
                    oldImageId = queryService.getImageId(fromImage);
                }

                registryService.pullImageWithPolicy(fromImage, imagePullManager, buildContext.getRegistryConfig(), buildConfig);

                String newImageId = queryService.getImageId(fromImage);

                removeDanglingImage(fromImage, oldImageId, newImageId, cleanupMode, false);
            }
        }
    }

    private void autoPullCacheFromImage(ImageConfiguration imageConfig, ImagePullManager imagePullManager, BuildContext buildContext) throws DockerAccessException, MojoExecutionException {
        if (imageConfig.getBuildConfiguration().getCacheFrom() == null) {
            return;
        }

        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
        CleanupMode cleanupMode = buildConfig.cleanupMode();

        for (String cacheFromImage : buildConfig.getCacheFrom()) {
            String oldImageId = null;
            if (cleanupMode.isRemove()) {
                oldImageId = queryService.getImageId(cacheFromImage);
            }

            try {
                registryService.pullImageWithPolicy(cacheFromImage, imagePullManager, buildContext.getRegistryConfig(), buildConfig);
            } catch (DockerAccessException e) {
                log.warn("Could not pull cacheFrom image: '%s'. Reason: %s", cacheFromImage, e.getMessage());
            }

            String newImageId = queryService.getImageId(cacheFromImage);

            removeDanglingImage(cacheFromImage, oldImageId, newImageId, cleanupMode, false);
        }
    }

    private String extractBaseFromConfiguration(BuildImageConfiguration buildConfig) {
        String fromImage;
        fromImage = buildConfig.getFrom();
        if (fromImage == null) {
            List<AssemblyConfiguration> assemblyConfig = buildConfig.getAllAssemblyConfigurations();
            if (assemblyConfig.isEmpty()) {
                fromImage = DockerAssemblyManager.DEFAULT_DATA_BASE_IMAGE;
            }
        }
        return fromImage;
    }

    static List<String> extractBaseFromDockerfile(BuildImageConfiguration buildConfig, MojoParameters mojoParameters, Map<String, String> buildArgs) {
        if (buildConfig.getDockerFile() == null || !buildConfig.getDockerFile().exists()) {
            if (buildConfig.getFrom() != null && !buildConfig.getFrom().isEmpty()) {
                return Collections.singletonList(buildConfig.getFrom());
            }
            return Collections.emptyList();
        }

        List<String> fromImage;
        try {
            File fullDockerFilePath = buildConfig.getAbsoluteDockerFilePath(mojoParameters);
            fromImage = DockerFileUtil.extractBaseImages(
                    fullDockerFilePath,
                    DockerFileUtil.createInterpolator(mojoParameters, buildConfig.getFilter()),
                    buildArgs);
        } catch (IOException e) {
            // Cant extract base image, so we wont try an auto pull. An error will occur later anyway when
            // building the image, so we are passive here.
            return Collections.emptyList();
        }
        return fromImage;
    }

    private void removeDanglingImage(String oldImageName, String oldImageId, String newImageId, CleanupMode cleanupMode, boolean force) throws DockerAccessException {
        if (oldImageId != null && !oldImageId.equals(newImageId)) {
            if (force) {
                removeImage(oldImageName, oldImageId, cleanupMode, true);
            } else {
                // Verify that the image is indeed dangling and remove it (or skip removal altogether).
                List<String> oldImageTags = docker.getImageTags(oldImageId);
                if (oldImageTags != null) {
                    if (oldImageTags.isEmpty()) {
                        removeImage(oldImageName, oldImageId, cleanupMode, false);
                    } else {
                        log.warn("%s: Skipped removing image %s; still tagged with: ", oldImageName, oldImageId, String.join(",", oldImageTags));
                    }
                }
            }
        }
    }

    private void removeImage(String oldImageName, String oldImageId, CleanupMode cleanupMode, boolean force) throws DockerAccessException {
        try {
            docker.removeImage(oldImageId, force);
            log.info("%s: Removed dangling image %s", oldImageName, oldImageId);
        } catch (DockerAccessException exp) {
            if (cleanupMode == CleanupMode.TRY_TO_REMOVE) {
                log.warn("%s: %s (dangling image)%s", oldImageName, exp.getMessage(),
                        (exp.getCause() != null ? " [" + exp.getCause().getMessage() + "]" : ""));
            } else {
                throw exp;
            }
        }
    }


    private boolean checkForSquash(ImageConfiguration imageConfig) {
        String squash = System.getProperty("docker.squash");
        if (squash != null) {
            return squash.length() == 0 || Boolean.valueOf(squash);
        } else {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            return buildConfig.squash();
        }
    }

    // ===========================================


    public static class BuildContext implements Serializable {

        private MojoParameters mojoParameters;

        private Map<String, String> buildArgs;

        private RegistryService.RegistryConfig registryConfig;

        public BuildContext() {
        }

        public MojoParameters getMojoParameters() {
            return mojoParameters;
        }

        public Map<String, String> getBuildArgs() {
            return buildArgs;
        }

        public RegistryService.RegistryConfig getRegistryConfig() {
            return registryConfig;
        }

        public static class Builder {

            private BuildContext context;

            public Builder() {
                this.context = new BuildContext();
            }

            public Builder(BuildContext context) {
                this.context = context;
            }

            public Builder mojoParameters(MojoParameters mojoParameters) {
                context.mojoParameters = mojoParameters;
                return this;
            }

            public Builder buildArgs(Map<String, String> buildArgs) {
                context.buildArgs = buildArgs;
                return this;
            }

            public Builder registryConfig(RegistryService.RegistryConfig registryConfig) {
                context.registryConfig = registryConfig;
                return this;
            }

            public BuildContext build() {
                return context;
            }
        }
    }

}
