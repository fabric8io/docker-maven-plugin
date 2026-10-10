package io.fabric8.maven.docker.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.AuthConfigList;
import io.fabric8.maven.docker.access.CreateImageOptions;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.BuildXConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.ImagePullPolicy;
import io.fabric8.maven.docker.service.helper.BuildArgResolver;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;

import io.fabric8.maven.docker.util.MojoParameters;
import io.fabric8.maven.docker.util.ProjectPaths;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Settings;

import static io.fabric8.maven.docker.service.BuildService.extractBaseFromDockerfile;
import static io.fabric8.maven.docker.service.BuildService.prepareBuildArgs;

/**
 * Allows to interact with registries, eg. to push/pull images.
 */
public class RegistryService {

    private final DockerAccess docker;
    private final QueryService queryService;
    private final BuildXService buildXService;
    private final Logger log;

    RegistryService(DockerAccess docker, QueryService queryService, BuildXService buildXService, Logger log) {
        this.docker = docker;
        this.queryService = queryService;
        this.buildXService = buildXService;
        this.log = log;
    }

    /**
     * Push a set of images to a registry
     *
     * @param imageConfigs images to push
     * @param retries how often to retry
     * @param registryConfig a global registry configuration
     * @param skipTag flag to skip pushing tagged images
     * @throws DockerAccessException
     * @throws MojoExecutionException
     */
    public void pushImages(ProjectPaths projectPaths, Collection<ImageConfiguration> imageConfigs,
                           int retries, RegistryConfig registryConfig, boolean skipTag, BuildService.BuildContext buildContext) throws DockerAccessException, MojoExecutionException {
        for (ImageConfiguration imageConfig : imageConfigs) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            if (buildConfig == null) {
                buildConfig = new BuildImageConfiguration.Builder().build();
            }
            if (buildConfig.skipPush()) {
                log.info("%s : Skipped pushing", imageConfig.getDescription());
                continue;
            }

            String name = imageConfig.getName();

            ImageName imageName = new ImageName(name);
            String configuredRegistry = EnvUtil.firstRegistryOf(
                imageName.getRegistry(),
                imageConfig.getRegistry(),
                registryConfig.getRegistry());

            BuildArgResolver buildArgResolver = new BuildArgResolver(log);
            Map<String, String> buildArgsFromExternalSources = buildArgResolver.resolveBuildArgs(buildContext);
            AuthConfig authConfigForLegacyPush = createAuthConfig(true, imageName.getUser(), configuredRegistry, registryConfig);

            if (buildConfig.isBuildX()) {
                AuthConfigList authConfigListForBuildXPush = createCompleteAuthConfigList(true, imageConfig, registryConfig, buildContext.getMojoParameters(), buildArgsFromExternalSources);
                buildXService.push(projectPaths, imageConfig, configuredRegistry, authConfigListForBuildXPush, buildArgsFromExternalSources);
            } else {
                dockerPush(retries, skipTag, buildConfig, name, configuredRegistry, authConfigForLegacyPush);
            }
        }
    }

    private void dockerPush(int retries, boolean skipTag, BuildImageConfiguration buildConfig, String name, String configuredRegistry, AuthConfig authConfig)
        throws DockerAccessException {
        long start = System.currentTimeMillis();
        docker.pushImage(name, authConfig, configuredRegistry, retries);
        log.info("Pushed %s in %s", name, EnvUtil.formatDurationTill(start));

        if (!skipTag) {
            for (String tag : buildConfig.getTags()) {
                if (tag != null) {
                    docker.pushImage(new ImageName(name, tag).getFullName(), authConfig, configuredRegistry, retries);
                }
            }
        }
    }


    /**
     *  Check an image, and, if <code>autoPull</code> is set to true, fetch it. Otherwise if the image
     *  is not existent, throw an error
     *
     * @param image image which is required to be pulled
     * @param pullManager image pull manager
     * @param registryConfig registry configuration
     * @param buildImageConfiguration image build configuration
     * @throws DockerAccessException in case of error in contacting docker daemon
     * @throws MojoExecutionException in case of any other misc failure
     */
    public void pullImageWithPolicy(String image, ImagePullManager pullManager, RegistryConfig registryConfig, BuildImageConfiguration buildImageConfiguration)
        throws DockerAccessException, MojoExecutionException {

        // Already pulled, so we don't need to take care
        if (pullManager.hasAlreadyPulled(image)) {
            return;
        }

        // Check if a pull is required
        if (!imageRequiresPull(queryService.hasImage(image), pullManager.getImagePullPolicy(), image)) {
            return;
        }

        final ImageName imageName = new ImageName(image);
        final long pullStartTime = System.currentTimeMillis();
        final String actualRegistry = EnvUtil.firstRegistryOf(imageName.getRegistry(), registryConfig.getRegistry());
        final CreateImageOptions createImageOptions = new CreateImageOptions(buildImageConfiguration != null ? buildImageConfiguration.getCreateImageOptions() : Collections.emptyMap())
            .fromImage(imageName.getNameWithoutTag(actualRegistry))
            .tag(imageName.getDigest() != null ? imageName.getDigest() : imageName.getTag());

        docker.pullImage(imageName.getFullName(),
            createAuthConfig(false, null, actualRegistry, registryConfig),
            actualRegistry, createImageOptions);
        log.info("Pulled %s in %s", imageName.getFullName(), EnvUtil.formatDurationTill(pullStartTime));
        pullManager.pulled(image);

        if (actualRegistry != null && !imageName.hasRegistry()) {
            // If coming from a registry which was not contained in the original name, add a tag from the
            // full name with the registry to the short name with no-registry.
            docker.tag(imageName.getFullName(actualRegistry), image, false);
        }
    }


    public static AuthConfigList createCompleteAuthConfigList(boolean isPush, ImageConfiguration imageConfig, RegistryConfig registryConfig, MojoParameters mojoParameters, Map<String, String> buildArgsFromExternalSources) throws MojoExecutionException {
        ImageName imageName = new ImageName(imageConfig.getName());
        String configuredRegistry = EnvUtil.firstRegistryOf(
            imageName.getRegistry(),
            imageConfig.getRegistry(),
            registryConfig.getRegistry());

        AuthConfig authConfig = registryConfig.createAuthConfig(isPush, imageName.getUser(), configuredRegistry);
        AuthConfigList authConfigList = createAuthConfigListForBaseImages(imageConfig.getBuildConfiguration(), mojoParameters, configuredRegistry, registryConfig, buildArgsFromExternalSources);
        if (authConfig != null) {
            authConfigList.addAuthConfig(authConfig);
        }

        addDockerHubAuthConfigForCloudDriver(imageConfig, configuredRegistry, mojoParameters, registryConfig, buildArgsFromExternalSources, authConfigList);
        addAuthConfigsForRegistryCaches(imageConfig, configuredRegistry, mojoParameters, registryConfig, buildArgsFromExternalSources, authConfigList);

        return authConfigList;
    }

    // BuildKit reads and writes a registry cache (cacheFrom/cacheTo of type=registry) with the credentials in the
    // buildx config.json, which only holds what is added here. Without this, a cache ref on a registry that is
    // neither the push registry nor the registry of a FROM image fails with 401 Unauthorized, and a failing
    // cache export fails the build. Registries already covered are skipped, so their existing entry (resolved
    // with the right push/pull context) is not overwritten.
    private static void addAuthConfigsForRegistryCaches(ImageConfiguration imageConfig, String configuredRegistry, MojoParameters mojoParameters, RegistryConfig registryConfig, Map<String, String> buildArgsFromExternalSources, AuthConfigList authConfigList) throws MojoExecutionException {
        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
        BuildXConfiguration buildX = buildConfig == null ? null : buildConfig.getBuildX();
        if (buildX == null) {
            return;
        }
        String cacheToRegistry = getRegistryOfRegistryCache(buildX.getCacheTo());
        String cacheFromRegistry = getRegistryOfRegistryCache(buildX.getCacheFrom());
        if (cacheToRegistry == null && cacheFromRegistry == null) {
            return;
        }

        List<String> covered = new ArrayList<>(getRegistriesForPull(buildConfig, mojoParameters, buildArgsFromExternalSources));
        covered.add(configuredRegistry);

        // Exporting a cache writes to the registry, so it needs push credentials
        if (cacheToRegistry != null) {
            addAuthConfigForRegistryCache(cacheToRegistry, true, covered, registryConfig, authConfigList);
        }
        if (cacheFromRegistry != null) {
            addAuthConfigForRegistryCache(cacheFromRegistry, false, covered, registryConfig, authConfigList);
        }
    }

    private static void addAuthConfigForRegistryCache(String registry, boolean isPush, List<String> covered, RegistryConfig registryConfig, AuthConfigList authConfigList) throws MojoExecutionException {
        for (String coveredRegistry : covered) {
            if (isSameRegistry(coveredRegistry, registry)) {
                return;
            }
        }
        covered.add(registry);
        AuthConfig cacheAuth = registryConfig.createAuthConfig(isPush, null, registry);
        if (cacheAuth != null) {
            authConfigList.addAuthConfig(cacheAuth);
        }
    }

    // A blank registry means docker.io
    private static boolean isSameRegistry(String registry1, String registry2) {
        if (isDockerHub(registry1) && isDockerHub(registry2)) {
            return true;
        }
        return StringUtils.isNotBlank(registry1) && registry1.equalsIgnoreCase(registry2);
    }

    /**
     * Get the registry host of a buildx cache option value, if it points to an image in a registry. Handles the
     * {@code type=registry,ref=<ref>[,...]} form and the bare {@code <ref>} shorthand of {@code --cache-from}.
     *
     * @return the registry, or null if the cache is not a registry cache or its ref does not name a registry
     */
    static String getRegistryOfRegistryCache(String cacheSpec) {
        if (StringUtils.isBlank(cacheSpec)) {
            return null;
        }
        String ref = null;
        if (cacheSpec.indexOf('=') < 0) {
            ref = cacheSpec.trim();
        } else {
            boolean registryType = true;
            for (String attribute : cacheSpec.split(",")) {
                String[] keyValue = attribute.trim().split("=", 2);
                if (keyValue.length != 2) {
                    continue;
                }
                if ("type".equals(keyValue[0])) {
                    registryType = "registry".equals(keyValue[1]);
                } else if ("ref".equals(keyValue[0])) {
                    ref = keyValue[1];
                }
            }
            if (!registryType) {
                return null;
            }
        }
        if (StringUtils.isBlank(ref)) {
            return null;
        }
        try {
            ImageName imageName = new ImageName(ref);
            return imageName.hasRegistry() ? imageName.getRegistry() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // A Docker Cloud builder is provisioned against Docker Hub regardless of the configured push
    // registry or the registry of the Dockerfile's FROM image, so docker.io credentials must be
    // present in the buildx config.json when the cloud driver is used, or builder creation fails.
    // If the push registry or a FROM image already resolves to docker.io, its auth entry already
    // covers this (and was resolved with the correct push/pull context); looking it up again here
    // would just add a redundant entry that, worse, could silently overwrite the existing one with
    // credentials resolved under the wrong (always-pull) context, so only add it when not already covered.
    private static void addDockerHubAuthConfigForCloudDriver(ImageConfiguration imageConfig, String configuredRegistry, MojoParameters mojoParameters, RegistryConfig registryConfig, Map<String, String> buildArgsFromExternalSources, AuthConfigList authConfigList) throws MojoExecutionException {
        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
        BuildXConfiguration buildX = buildConfig == null ? null : buildConfig.getBuildX();
        if (buildX == null || !buildX.isCloudDriver()) {
            return;
        }
        if (isDockerHub(configuredRegistry)
            || getRegistriesForPull(buildConfig, mojoParameters, buildArgsFromExternalSources).stream().anyMatch(RegistryService::isDockerHub)) {
            return;
        }
        AuthConfig dockerHubAuth = registryConfig.createAuthConfig(false, cloudBuilderOrg(buildX), AuthConfig.REGISTRY_DOCKER_IO);
        if (dockerHubAuth != null) {
            authConfigList.addAuthConfig(dockerHubAuth);
        }
    }

    // The cloud endpoint is always in <org>/<name> form, with <org> naming the Docker Hub organization
    // that owns the builder. Passing it as the user lets a settings.xml <server><id>docker.io/<org></id></server>
    // entry be picked up in preference to a plain docker.io one, the same way a pushed image's own registry
    // user already is (see AuthConfigFactory#checkForServer); it still falls back to a plain docker.io entry
    // when no such org-specific server is configured, or when builderName isn't yet in <org>/<name> form.
    private static String cloudBuilderOrg(BuildXConfiguration buildX) {
        String builderName = buildX.getBuilderName();
        int slash = builderName == null ? -1 : builderName.indexOf('/');
        return slash > 0 ? builderName.substring(0, slash) : null;
    }

    private static boolean isDockerHub(String registry) {
        if (StringUtils.isBlank(registry)) {
            return true;
        }
        return AuthConfig.REGISTRY_DOCKER_IO.equalsIgnoreCase(StringUtils.substringBefore(registry, "/"));
    }

    public static AuthConfigList createAuthConfigListForBaseImages(BuildImageConfiguration buildConfig, MojoParameters mojoParameters, String configuredRegistry, RegistryConfig registryConfig, Map<String, String> buildArgsFromExternalSources) throws MojoExecutionException {
        AuthConfigList authConfigList = new AuthConfigList();
        Set<String> fromRegistries = getRegistriesForPull(buildConfig, mojoParameters, buildArgsFromExternalSources);
        for (String fromRegistry : fromRegistries) {
            if (StringUtils.isNotBlank(configuredRegistry) && configuredRegistry.equalsIgnoreCase(fromRegistry)) {
                continue;
            }
            AuthConfig additionalAuth = registryConfig.createAuthConfig(false, null, fromRegistry);
            if (additionalAuth != null) {
                authConfigList.addAuthConfig(additionalAuth);
            }
        }
        return authConfigList;
    }

    // ============================================================================================================

    private static Set<String> getRegistriesForPull(BuildImageConfiguration buildConfig, MojoParameters mojoParameters, Map<String, String> buildArgsFromExternalSources) {
        Set<String> registries = new HashSet<>();
        List<String> fromImages = extractBaseFromDockerfile(buildConfig, mojoParameters, prepareBuildArgs(buildArgsFromExternalSources, buildConfig));
        for (String fromImage : fromImages) {
            ImageName imageName = new ImageName(fromImage);

            if (imageName.hasRegistry()) {
                registries.add(imageName.getRegistry());
            }
        }
        return registries;
    }

    private boolean imageRequiresPull(boolean hasImage, ImagePullPolicy pullPolicy, String imageName)
        throws MojoExecutionException {

        // The logic here is like this (see also #96):
        // otherwise: don't pull

        if (pullPolicy == ImagePullPolicy.Never) {
            if (!hasImage) {
                throw new MojoExecutionException(
                    String.format("No image '%s' found and pull policy 'Never' is set. Please chose another pull policy or pull the image yourself)", imageName));
            }
            return false;
        }

        // If the image is not available and mode is not ImagePullPolicy.Never --> pull
        if (!hasImage) {
            return true;
        }

        // If pullPolicy == Always --> pull, otherwise not (we have it already)
        return pullPolicy == ImagePullPolicy.Always;
    }

    private AuthConfig createAuthConfig(boolean isPush, String user, String registry, RegistryConfig config)
            throws MojoExecutionException {

        return config.createAuthConfig(isPush, user, registry);
    }

    // ===========================================


    public static class RegistryConfig implements Serializable {

        private String registry;

        private Settings settings;

        private AuthConfigFactory authConfigFactory;

        private boolean skipExtendedAuth;

        private Map authConfig;

        public RegistryConfig() {
        }

        public String getRegistry() {
            return registry;
        }

        public Settings getSettings() {
            return settings;
        }

        public AuthConfigFactory getAuthConfigFactory() {
            return authConfigFactory;
        }

        public boolean isSkipExtendedAuth() {
            return skipExtendedAuth;
        }

        public Map getAuthConfig() {
            return authConfig;
        }

        public AuthConfig createAuthConfig(boolean isPush, String user, String registry) throws MojoExecutionException {
            return authConfigFactory.createAuthConfig(isPush, skipExtendedAuth, authConfig, settings, user, registry);
        }

        public static class Builder {

            private RegistryConfig context;

            public Builder() {
                this.context = new RegistryConfig();
            }

            public Builder(RegistryConfig context) {
                this.context = context;
            }

            public Builder registry(String registry) {
                context.registry = registry;
                return this;
            }

            public Builder settings(Settings settings) {
                context.settings = settings;
                return this;
            }

            public Builder authConfigFactory(AuthConfigFactory authConfigFactory) {
                context.authConfigFactory = authConfigFactory;
                return this;
            }

            public Builder skipExtendedAuth(boolean skipExtendedAuth) {
                context.skipExtendedAuth = skipExtendedAuth;
                return this;
            }

            public Builder authConfig(Map authConfig) {
                context.authConfig = authConfig;
                return this;
            }

            public RegistryConfig build() {
                return context;
            }
        }
    }

}
