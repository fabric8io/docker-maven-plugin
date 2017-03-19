package io.fabric8.maven.docker.service;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.fabric8.maven.docker.access.BuildOptions;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.CleanupMode;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.DockerFileUtil;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.ImagePullCacheManager;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;

import com.google.common.collect.ImmutableMap;

import org.apache.maven.plugin.MojoExecutionException;

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
     * @param imageConfig the image configuration
     * @param buildContext the build context
     * @throws DockerAccessException
     * @throws MojoExecutionException
     */
    public void buildImage(ImageConfiguration imageConfig, BuildContext buildContext)
            throws DockerAccessException, MojoExecutionException {

        autoPullBaseImage(imageConfig, buildContext);

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

        long time = System.currentTimeMillis();

        if (buildConfig.getDockerArchive() != null) {
            docker.loadImage(imageName, buildConfig.getAbsoluteDockerTarPath(params));
            log.info("%s: Loaded tarball in %s", buildConfig.getDockerArchive(), EnvUtil.formatDurationTill(time));
            return;
        }

        File dockerArchive = archiveService.createArchive(imageName, buildConfig, params, log);
        log.info("%s: Created %s in %s", imageConfig.getDescription(), dockerArchive.getName(), EnvUtil.formatDurationTill(time));

        Map<String, String> mergedBuildMap = prepareBuildArgs(buildArgs, buildConfig);

        // auto is now supported by docker, consider switching?
        BuildOptions opts =
                new BuildOptions(buildConfig.getBuildOptions())
                        .dockerfile(getDockerfileName(buildConfig))
                        .forceRemove(cleanupMode.isRemove())
                        .noCache(noCache)
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
        return ImmutableMap.<String, String>builder()
                .putAll(buildContext.getBuildArgs() != null ? buildContext.getBuildArgs() : Collections.<String, String>emptyMap())
                .putAll(buildArgsFromProject)
                .putAll(buildArgsFromSystem)
                .build();
    }

    private Map<String, String> addBuildArgsFromProperties(Properties properties) {
        String argPrefix = "docker.buildArg.";
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

    private void autoPullBaseImage(ImageConfiguration imageConfig, BuildContext buildContext)
            throws DockerAccessException, MojoExecutionException {
        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();

        if (buildConfig.getDockerArchive() != null) {
            // No auto pull needed in archive mode
            return;
        }

        String fromImage;
        if (buildConfig.isDockerFileMode()) {
            fromImage = extractBaseFromDockerfile(buildConfig, buildContext);
        } else {
            fromImage = extractBaseFromConfiguration(buildConfig);
        }
        if (fromImage != null && !DockerAssemblyManager.SCRATCH_IMAGE.equals(fromImage)) {
            String pullRegistry =
                    EnvUtil.findRegistry(new ImageName(fromImage).getRegistry(), buildContext.getPullRegistry(), buildContext.getRegistryConfig().getRegistry());

            registryService.checkImageWithAutoPull(fromImage, pullRegistry, true, buildContext.getRegistryConfig());
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

    private String extractBaseFromDockerfile(BuildImageConfiguration buildConfig, BuildContext buildContext) {
        String fromImage;
        try {
            File fullDockerFilePath = buildConfig.getAbsoluteDockerFilePath(buildContext.getMojoParameters());
            fromImage = DockerFileUtil.extractBaseImage(fullDockerFilePath);
        } catch (IOException e) {
            // Cant extract base image, so we wont try an auto pull. An error will occur later anyway when
            // building the image, so we are passive here.
            fromImage = null;
        }
        return fromImage;
    }

    private boolean checkForNocache(ImageConfiguration imageConfig) {
        String nocache = System.getProperty("docker.nocache");
        if (nocache != null) {
            return nocache.length() == 0 || Boolean.valueOf(nocache);
        } else {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            return buildConfig.nocache();
        }
    }

    private boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }


    // ===========================================


    public static class BuildContext implements Serializable {

        private MojoParameters mojoParameters;

        private Map<String, String> buildArgs;

        private String pullRegistry;

        private RegistryService.RegistryConfig registryConfig;

        public BuildContext() {
        }

        public MojoParameters getMojoParameters() {
            return mojoParameters;
        }

        public Map<String, String> getBuildArgs() {
            return buildArgs;
        }

        public String getPullRegistry() {
            return pullRegistry;
        }

        public RegistryService.RegistryConfig getRegistryConfig() {
            return registryConfig;
        }

        public static class Builder {

            private BuildContext context = new BuildContext();

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

            public Builder pullRegistry(String pullRegistry) {
                context.pullRegistry = pullRegistry;
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
