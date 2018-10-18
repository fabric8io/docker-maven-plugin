package io.fabric8.maven.docker.build.docker;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import com.google.common.collect.ImmutableMap;
import io.fabric8.maven.docker.access.BuildOptions;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.build.BuildContext;
import io.fabric8.maven.docker.build.BuildService;
import io.fabric8.maven.docker.build.RegistryService;
import io.fabric8.maven.docker.build.maven.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.build.AssemblyConfiguration;
import io.fabric8.maven.docker.config.build.BuildImageConfiguration;
import io.fabric8.maven.docker.config.build.CleanupMode;
import io.fabric8.maven.docker.config.build.ImagePullPolicy;
import io.fabric8.maven.docker.util.DockerFileUtil;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;

public class DockerBuildService implements BuildService {

    private final DockerAccess docker;
    private final RegistryService registryService;
    private final Logger log;

    public DockerBuildService(DockerAccess docker, RegistryService registryService, Logger log) {
        this.docker = docker;
        this.registryService = registryService;
        this.log = log;
    }

    /**
     * Pull the base image if needed and run the build.
     *
     * @param imageConfig the image configuration
     * @param buildContext the build context
     */
    @Override
    public void buildImage(ImageConfiguration imageConfig, BuildContext buildContext, Map<String, String> buildArgs)
            throws IOException {
            // Call a pre-hook to the build
            autoPullBaseImageIfRequested(imageConfig, buildContext);

            String imageName = imageConfig.getName();
            ImageName.validate(imageName);
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();

            // Load an archive if present
            if (buildConfig.getDockerArchive() != null) {
                loadImageFromArchive(imageName, buildContext, buildConfig.getDockerArchive());
                return;
            }

            // Get old image id (if requested
            Optional<String> oldImageId = getOldImageId(imageName, buildConfig);

            // Create an archive usable for sending to the Docker daemon
            File dockerArchive = createDockerContextArchive(imageConfig, buildContext);

            // Prepare options for building against a Docker daemon and do the build
            String newImageId = build(imageConfig,
                                      getBuildArgsFromProperties(buildContext, buildArgs),
                                      dockerArchive);

            // Remove the image if requested
            if (oldImageId.isPresent() && !oldImageId.get().equals(newImageId)) {
                removeOldImage(imageConfig, oldImageId.get());
            }
    }

    public void tagImage(String imageName, ImageConfiguration imageConfig) throws DockerAccessException {
        List<String> tags = imageConfig.getBuildConfiguration().getTags();
        if (!tags.isEmpty()) {
            log.info("%s: Tag with %s", imageConfig.getDescription(), EnvUtil.stringJoin(tags, ","));

            for (String tag : tags) {
                if (tag != null) {
                    docker.tag(imageName, new ImageName(imageName, tag).getFullName(), true);
                }
            }

            log.debug("Tagging image successful!");
        }
    }


    private void autoPullBaseImageIfRequested(ImageConfiguration imageConfig, BuildContext buildContext) throws IOException {
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
        if (fromImage != null && !"scratch".equals(fromImage)) {

            ImagePullPolicy imagePullPolicy =
                buildConfig.getImagePullPolicy() != null ?
                    createPullPolicy(buildConfig.getImagePullPolicy()) :
                    buildContext.getRegistryContext().getDefaultImagePullPolicy();

            registryService.pullImage(fromImage, imagePullPolicy, buildContext.getRegistryContext());
        }
    }

    private ImagePullPolicy createPullPolicy(String imagePullPolicy) {
        if (imagePullPolicy != null) {
            return ImagePullPolicy.fromString(imagePullPolicy);
        }
        return ImagePullPolicy.IfNotPresent;
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

    private String extractBaseFromDockerfile(BuildImageConfiguration buildConfig, BuildContext ctx) {
        String fromImage;
        try {
            final File fullDockerFilePath =
                EnvUtil.prepareAbsoluteSourceDirPath(ctx, buildConfig.getDockerFile().getPath());
            fromImage = DockerFileUtil.extractBaseImage(
                fullDockerFilePath,
                ctx.createInterpolator(buildConfig.getFilter()));

        } catch (IOException e) {
            // Cant extract base image, so we wont try an auto pull. An error will occur later anyway when
            // building the image, so we are passive here.
            fromImage = null;
        }
        return fromImage;
    }


    private void loadImageFromArchive(String imageName, BuildContext ctx, File dockerArchive) throws DockerAccessException {
        long time = System.currentTimeMillis();
        File dockerArchiveAbsolute = EnvUtil.prepareAbsoluteSourceDirPath(ctx, dockerArchive.getPath());
        docker.loadImage(imageName, dockerArchiveAbsolute);
        log.info("%s: Loaded tarball in %s", dockerArchive, EnvUtil.formatDurationTill(time));
    }

    private File createDockerContextArchive(ImageConfiguration imageConfig, BuildContext ctx) throws IOException {
        long time = System.currentTimeMillis();
        String imageName = imageConfig.getName();
        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
        File dockerContextArchive = ctx.createImageContentArchive(imageName, buildConfig, log);
        log.info("%s: Created %s in %s",
                 imageConfig.getDescription(),
                 dockerContextArchive.getName(),
                 EnvUtil.formatDurationTill(time));
        return dockerContextArchive;
    }

    private Optional<String> getOldImageId(String imageName, BuildImageConfiguration buildConfig) throws DockerAccessException {
        CleanupMode cleanupMode = CleanupMode.parse(buildConfig.getCleanupMode());
        return cleanupMode.isRemove() ?
            Optional.ofNullable(docker.getImageId(imageName)) :
            Optional.empty();
    }

    private String build(ImageConfiguration imageConfig,
                         Map<String, String> buildArgs,
                         File dockerArchive) throws DockerAccessException {
        String imageName = imageConfig.getName();
        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
        boolean noCache = checkForNocache(imageConfig);
        BuildOptions opts =
                new BuildOptions(buildConfig.getBuildOptions())
                        .dockerfile(getDockerfileName(buildConfig))
                        .forceRemove(CleanupMode.parse(buildConfig.getCleanupMode()).isRemove())
                        .noCache(noCache)
                        .buildArgs(prepareBuildArgs(buildArgs, buildConfig));
        docker.buildImage(imageName, dockerArchive, opts);
        String newImageId = docker.getImageId(imageName);
        log.info("%s: Built image %s", imageConfig.getDescription(), newImageId);
        return newImageId;
    }

    private void removeOldImage(ImageConfiguration imageConfig, String oldImageId) throws DockerAccessException {
        try {
            docker.removeImage(oldImageId, true);
            log.info("%s: Removed old image %s", imageConfig.getDescription(), oldImageId);
        } catch (DockerAccessException exp) {
            String cleanup = imageConfig.getBuildConfiguration().getCleanupMode();
            if (CleanupMode.parse(cleanup) == CleanupMode.TRY_TO_REMOVE) {
                log.warn("%s: %s (old image)%s", imageConfig.getDescription(), exp.getMessage(),
                         (exp.getCause() != null ? " [" + exp.getCause().getMessage() + "]" : ""));
            } else {
                throw exp;
            }
        }
    }

    private Map<String, String> prepareBuildArgs(Map<String, String> buildArgs, BuildImageConfiguration buildConfig) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        if (buildArgs != null) {
            builder.putAll(buildArgs);
        }
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

    private Map<String, String> getBuildArgsFromProperties(BuildContext buildContext, Map<String, String> buildArgs) {
        Map<String, String> buildArgsFromProject = getBuildArgsFromProperties(buildContext.getProperties());
        Map<String, String> buildArgsFromSystem = getBuildArgsFromProperties(System.getProperties());
        return ImmutableMap.<String, String>builder()
                .putAll(Optional.of(buildArgs).orElse(Collections.emptyMap()))
                .putAll(buildArgsFromProject)
                .putAll(buildArgsFromSystem)
                .build();
    }

    private Map<String, String> getBuildArgsFromProperties(Properties properties) {
        String argPrefix = "docker.buildArg.";
        Map<String, String> buildArgs = new HashMap<>();
        if (properties == null) {
            return buildArgs;
        }
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
}
