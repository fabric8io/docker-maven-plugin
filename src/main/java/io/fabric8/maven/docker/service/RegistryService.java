package io.fabric8.maven.docker.service;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.CreateImageOptions;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.ImagePullPolicy;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;

import io.fabric8.maven.docker.util.ProjectPaths;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Settings;

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
     * @param imageConfigs images to push (but only if they have a build configuration)
     * @param retries how often to retry
     * @param registryConfig a global registry configuration
     * @param skipTag flag to skip pushing tagged images
     * @throws DockerAccessException
     * @throws MojoExecutionException
     */
    public void pushImages(ProjectPaths projectPaths, Collection<ImageConfiguration> imageConfigs,
                            int retries, RegistryConfig registryConfig, boolean skipTag) throws DockerAccessException, MojoExecutionException {
        for (ImageConfiguration imageConfig : imageConfigs) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            if (buildConfig == null || buildConfig.skipPush()) {
                log.info("%s : Skipped pushing", imageConfig.getDescription());
                continue;
            }

            String name = imageConfig.getName();

            ImageName imageName = new ImageName(name);
            String configuredRegistry = EnvUtil.firstRegistryOf(
                imageName.getRegistry(),
                imageConfig.getRegistry(),
                registryConfig.getRegistry());


            AuthConfig authConfig = createAuthConfig(true, imageName.getUser(), configuredRegistry, registryConfig);
            if (imageConfig.isBuildX()) {
                buildXService.push(projectPaths, imageConfig, configuredRegistry, authConfig);
            } else {
                dockerPush(retries, skipTag, buildConfig, name, configuredRegistry, authConfig);
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


    // ============================================================================================================


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
