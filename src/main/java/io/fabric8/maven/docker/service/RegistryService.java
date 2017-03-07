package io.fabric8.maven.docker.service;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.ImagePullCache;
import io.fabric8.maven.docker.util.ImagePullCacheManager;
import io.fabric8.maven.docker.util.Logger;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Settings;

/**
 * Allows to interact with registries, eg. to push/pull images.
 */
public class RegistryService {

    private final DockerAccess docker;
    private final QueryService queryService;
    private final Logger log;

    RegistryService(DockerAccess docker, QueryService queryService, Logger log) {
        this.docker = docker;
        this.queryService = queryService;
        this.log = log;
    }

    public void pushImages(Collection<ImageConfiguration> imageConfigs, String pushRegistry, int retries, RegistryConfig registryConfig) throws DockerAccessException, MojoExecutionException {
        for (ImageConfiguration imageConfig : imageConfigs) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            String name = imageConfig.getName();
            if (buildConfig != null) {
                String configuredRegistry = EnvUtil.findRegistry(new ImageName(imageConfig.getName()).getRegistry(),
                        imageConfig.getRegistry(), pushRegistry, registryConfig.getRegistry());


                AuthConfig authConfig = createAuthConfig(true, new ImageName(name).getUser(), configuredRegistry, registryConfig);

                long start = System.currentTimeMillis();
                docker.pushImage(name, authConfig, configuredRegistry, retries);
                log.info("Pushed %s in %s", name, EnvUtil.formatDurationTill(start));

                for (String tag : imageConfig.getBuildConfiguration().getTags()) {
                    if (tag != null) {
                        docker.pushImage(new ImageName(name, tag).getFullName(), authConfig, configuredRegistry, retries);
                    }
                }
            }
        }
    }


    /**
     * Check an image, and, if <code>autoPull</code> is set to true, fetch it. Otherwise if the image
     * is not existent, throw an error
     * @param image image name
     * @param registry optional registry which is used if the image itself doesn't have a registry.
     * @param autoPullAlwaysAllowed whether an unconditional autopull is allowed.
     * @throws DockerAccessException
     * @throws MojoExecutionException
     */
    public void checkImageWithAutoPull(String image, String registry, boolean autoPullAlwaysAllowed, RegistryConfig registryConfig) throws DockerAccessException, MojoExecutionException {
        // TODO: further refactoring could be done to avoid referencing the QueryService here
        ImagePullCache previouslyPulledCache = registryConfig.getImagePullCacheManager().load();
        if (!queryService.imageRequiresAutoPull(registryConfig.getAutoPull(), image, autoPullAlwaysAllowed, previouslyPulledCache)) {
            return;
        }

        ImageName imageName = new ImageName(image);
        long time = System.currentTimeMillis();
        String actualRegistry = EnvUtil.findRegistry(imageName.getRegistry(), registry);
        docker.pullImage(imageName.getFullName(), createAuthConfig(false, null, actualRegistry, registryConfig), actualRegistry);
        log.info("Pulled %s in %s", imageName.getFullName(), EnvUtil.formatDurationTill(time));
        previouslyPulledCache.add(image);
        registryConfig.getImagePullCacheManager().save(previouslyPulledCache);

        if (registry != null && !imageName.hasRegistry()) {
            // If coming from a registry which was not contained in the original name, add a tag from the
            // full name with the registry to the short name with no-registry.
            docker.tag(imageName.getFullName(registry), image, false);
        }
    }

    private AuthConfig createAuthConfig(boolean isPush, String user, String registry, RegistryConfig config)
            throws MojoExecutionException {

        return config.getAuthConfigFactory().createAuthConfig(isPush, config.isSkipExtendedAuth(), config.getAuthConfig(),
                config.getSettings(), user, registry);
    }

    // ===========================================


    public static class RegistryConfig implements Serializable {

        private String registry;

        private String autoPull;

        private ImagePullCacheManager imagePullCacheManager;

        private Settings settings;

        private AuthConfigFactory authConfigFactory;

        private boolean skipExtendedAuth;

        private Map authConfig;

        public RegistryConfig() {
        }

        public String getRegistry() {
            return registry;
        }

        public String getAutoPull() {
            return autoPull;
        }

        public ImagePullCacheManager getImagePullCacheManager() {
            return imagePullCacheManager;
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

        public static class Builder {

            private RegistryConfig context = new RegistryConfig();

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

            public Builder autoPull(String autoPull) {
                context.autoPull = autoPull;
                return this;
            }

            public Builder imagePullCacheManager(ImagePullCacheManager imagePullCacheManager) {
                context.imagePullCacheManager = imagePullCacheManager;
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
