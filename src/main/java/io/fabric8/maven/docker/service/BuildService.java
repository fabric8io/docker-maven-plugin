package io.fabric8.maven.docker.service;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.PatternSyntaxException;

import org.apache.maven.plugin.MojoExecutionException;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import io.fabric8.maven.docker.access.BuildOptions;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.CleanupMode;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.model.ImageArchiveManifest;
import io.fabric8.maven.docker.model.ImageArchiveManifestEntry;
import io.fabric8.maven.docker.util.DockerFileUtil;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageArchiveUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import io.fabric8.maven.docker.util.NamePatternUtil;

public class BuildService {

    private final String argPrefix = "docker.buildArg.";

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
     * @param imageConfig the image configuration
     * @param buildContext the build context
     * @throws DockerAccessException
     * @throws MojoExecutionException
     */
    public void buildImage(ImageConfiguration imageConfig, ImagePullManager imagePullManager, BuildContext buildContext)
            throws DockerAccessException, MojoExecutionException {

        if (imagePullManager != null) {
            autoPullBaseImage(imageConfig, imagePullManager, buildContext);
        }

        buildImage(imageConfig, buildContext.getMojoParameters(), checkForNocache(imageConfig), addBuildArgs(buildContext));
    }

    public void tagImage(String imageName, ImageConfiguration imageConfig) throws DockerAccessException {

        List<String> tags = imageConfig.getBuildConfiguration().getTags();
        if (tags.size() > 0) {
            log.info("%s: Tag with %s", imageConfig.getDescription(), EnvUtil.stringJoin(tags, ","));

            for (String tag : tags) {
                if (tag != null) {
                    docker.tag(imageName, new ImageName(imageName, tag).getFullName(), true);
                }
            }

            log.debug("Tagging image successful!");
        }
    }

    /**
     * Build an image
     *
     * @param imageConfig the image configuration
     * @param params mojo params for the project
     * @param noCache if not null, dictate the caching behaviour. Otherwise its taken from the build configuration
     * @param buildArgs
     * @throws DockerAccessException
     * @throws MojoExecutionException
     */
    protected void buildImage(ImageConfiguration imageConfig, MojoParameters params, boolean noCache, Map<String, String> buildArgs)
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

            if(archiveImageName != null && !archiveImageName.equals(imageName)) {
                docker.tag(archiveImageName, imageName, true);
            }

            return;
        }

        long time = System.currentTimeMillis();

        File dockerArchive = archiveService.createArchive(imageName, buildConfig, params, log);
        log.info("%s: Created %s in %s", imageConfig.getDescription(), dockerArchive.getName(), EnvUtil.formatDurationTill(time));

        Map<String, String> mergedBuildMap = prepareBuildArgs(buildArgs, buildConfig);

        // auto is now supported by docker, consider switching?
        BuildOptions opts =
                new BuildOptions(buildConfig.getBuildOptions())
                        .dockerfile(getDockerfileName(buildConfig))
                        .forceRemove(cleanupMode.isRemove())
                        .noCache(noCache)
                        .cacheFrom(buildConfig.getCacheFrom())
                        .buildArgs(mergedBuildMap);
        String newImageId = doBuildImage(imageName, dockerArchive, opts);
        log.info("%s: Built image %s", imageConfig.getDescription(), newImageId);

        if (oldImageId != null && !oldImageId.equals(newImageId)) {
            try {
                docker.removeImage(oldImageId, true);
                log.info("%s: Removed old image %s", imageConfig.getDescription(), oldImageId);
            } catch (DockerAccessException exp) {
                if (cleanupMode == CleanupMode.TRY_TO_REMOVE) {
                    log.warn("%s: %s (old image)%s", imageConfig.getDescription(), exp.getMessage(),
                            (exp.getCause() != null ? " [" + exp.getCause().getMessage() + "]" : ""));
                } else {
                    throw exp;
                }
            }
        }
    }

    private Map<String, String> prepareBuildArgs(Map<String, String> buildArgs, BuildImageConfiguration buildConfig) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String>builder().putAll(buildArgs);
        if (buildConfig.getArgs() != null) {
            builder.putAll(buildConfig.getArgs());
        }
        return builder.build();
    }

    private String getArchiveImageName(BuildImageConfiguration buildConfig, File tarArchive) throws MojoExecutionException {
        if(buildConfig.getLoadNamePattern() == null || buildConfig.getLoadNamePattern().length() == 0) {
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
        } catch(PatternSyntaxException e) {
            throw new MojoExecutionException("Unable to interpret loadNamePattern " + buildConfig.getLoadNamePattern(), e);
        }

        if(archiveImageName == null) {
            throw new MojoExecutionException("No image in the archive has a tag that matches pattern " + buildConfig.getLoadNamePattern());
        }

        return archiveImageName;
    }

    private ImageArchiveManifest readArchiveManifest(File tarArchive) throws IOException, JsonParseException {
        long time = System.currentTimeMillis();

        ImageArchiveManifest manifest = ImageArchiveUtil.readManifest(tarArchive);

        log.info("%s: Read archive manifest in %s", tarArchive, EnvUtil.formatDurationTill(time));

        // Show the results of reading the manifest to users trying to debug their configuration
        if(log.isDebugEnabled()) {
            for(ImageArchiveManifestEntry entry : manifest.getEntries()) {
                log.debug("Entry ID: %s has %d repo tag(s)", entry.getId(), entry.getRepoTags().size());
                for(String repoTag : entry.getRepoTags()) {
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
        if(log.isDebugEnabled()) {
            for(Map.Entry<String, ImageArchiveManifestEntry> entry : entries.entrySet()) {
                log.debug("Repo tag pattern matched %s referring to image %s", entry.getKey(), entry.getValue().getId());
            }
        }

        if(!entries.isEmpty()) {
            Map.Entry<String, ImageArchiveManifestEntry> matchedEntry = entries.entrySet().iterator().next();

            if(ImageArchiveUtil.mapEntriesById(entries.values()).size() > 1) {
                log.warn("Multiple image ids matched pattern %s: using tag %s associated with id %s",
                        imageNamePattern, matchedEntry.getKey(), matchedEntry.getValue().getId());
            } else {
                log.info("Using image tag %s from archive", matchedEntry.getKey());
            }

            return matchedEntry.getKey();
        }

        return null;
    }

    private String getDockerfileName(BuildImageConfiguration buildConfig) {
        if (buildConfig.isDockerFileMode()) {
            return buildConfig.getDockerFile().getName();
        } else {
            return null;
        }
    }

    private String doBuildImage(String imageName, File dockerArchive, BuildOptions options)
            throws DockerAccessException, MojoExecutionException {
        docker.buildImage(imageName, dockerArchive, options);
        return queryService.getImageId(imageName);
    }

    private Map<String, String> addBuildArgs(BuildContext buildContext) {
        Map<String, String> buildArgsFromProject = addBuildArgsFromProperties(buildContext.getMojoParameters().getProject().getProperties());
        Map<String, String> buildArgsFromSystem = addBuildArgsFromProperties(System.getProperties());
        Map<String, String> buildArgsFromDockerConfig = addBuildArgsFromDockerConfig();
        return ImmutableMap.<String, String>builder()
                .putAll(buildArgsFromDockerConfig)
                .putAll(buildContext.getBuildArgs() != null ? buildContext.getBuildArgs() : Collections.<String, String>emptyMap())
                .putAll(buildArgsFromProject)
                .putAll(buildArgsFromSystem)
                .build();
    }

    private Map<String, String> addBuildArgsFromProperties(Properties properties) {
        Map<String, String> buildArgs = new HashMap<>();
        for (Object keyObj : properties.keySet()) {
            String key = (String) keyObj;
            if (key.startsWith(argPrefix)) {
                String argKey = key.replaceFirst(argPrefix, "");
                String value = properties.getProperty(key);

                if (!isEmpty(value)) {
                    buildArgs.put(argKey, value);
                }
            }
        }
        log.debug("Build args set %s", buildArgs);
        return buildArgs;
    }

    private Map<String, String> addBuildArgsFromDockerConfig() {
        JsonObject dockerConfig = DockerFileUtil.readDockerConfig();
        if (dockerConfig == null) {
            return Collections.emptyMap();
        }

        // add proxies
        Map<String, String> buildArgs = new HashMap<>();
        if (dockerConfig.has("proxies")) {
            JsonObject proxies = dockerConfig.getAsJsonObject("proxies");
            if (proxies.has("default")) {
                JsonObject defaultProxyObj = proxies.getAsJsonObject("default");
                String[] proxyMapping = new String[] {
                        "httpProxy", "http_proxy",
                        "httpsProxy", "https_proxy",
                        "noProxy", "no_proxy",
                        "ftpProxy", "ftp_proxy"
                };

                for(int index = 0; index < proxyMapping.length; index += 2) {
                    if (defaultProxyObj.has(proxyMapping[index])) {
                        buildArgs.put(proxyMapping[index+1], defaultProxyObj.get(proxyMapping[index]).getAsString());
                    }
                }
            }
        }
        log.debug("Build args set %s", buildArgs);
        return buildArgs;
    }

    private void autoPullBaseImage(ImageConfiguration imageConfig, ImagePullManager imagePullManager, BuildContext buildContext)
            throws DockerAccessException, MojoExecutionException {
        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();

        if (buildConfig.getDockerArchive() != null) {
            // No auto pull needed in archive mode
            return;
        }

        List<String> fromImages;
        if (buildConfig.isDockerFileMode()) {
            fromImages = extractBaseFromDockerfile(buildConfig, buildContext);
        } else {
            fromImages = new LinkedList<>();
            String baseImage = extractBaseFromConfiguration(buildConfig);
            if (baseImage!=null) {
                fromImages.add(extractBaseFromConfiguration(buildConfig));
            }
        }
        for (String fromImage : fromImages) {
            if (fromImage != null && !DockerAssemblyManager.SCRATCH_IMAGE.equals(fromImage)) {
                registryService.pullImageWithPolicy(fromImage, imagePullManager, buildContext.getRegistryConfig(), queryService.hasImage(fromImage));
            }
        }
    }

    private String extractBaseFromConfiguration(BuildImageConfiguration buildConfig) {
        String fromImage;
        fromImage = buildConfig.getFrom();
        if (fromImage == null) {
            AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();
            if (assemblyConfig == null) {
                fromImage = DockerAssemblyManager.DEFAULT_DATA_BASE_IMAGE;
            }
        }
        return fromImage;
    }

    private List<String> extractBaseFromDockerfile(BuildImageConfiguration buildConfig, BuildContext buildContext) {
        List<String> fromImage;
        try {
            File fullDockerFilePath = buildConfig.getAbsoluteDockerFilePath(buildContext.getMojoParameters());
            fromImage = DockerFileUtil.extractBaseImages(
                fullDockerFilePath,
                DockerFileUtil.createInterpolator(buildContext.getMojoParameters(), buildConfig.getFilter()));
        } catch (IOException e) {
            // Cant extract base image, so we wont try an auto pull. An error will occur later anyway when
            // building the image, so we are passive here.
            return Collections.emptyList();
        }
        return fromImage;
    }

    private boolean checkForNocache(ImageConfiguration imageConfig) {
        String noCache = System.getProperty("docker.noCache");
        if (noCache == null) {
            noCache = System.getProperty("docker.nocache");
        }
        if (noCache != null) {
            return noCache.length() == 0 || Boolean.valueOf(noCache);
        } else {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            return buildConfig.noCache();
        }
    }

    private boolean isEmpty(String str) {
        return str == null || str.isEmpty();
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
