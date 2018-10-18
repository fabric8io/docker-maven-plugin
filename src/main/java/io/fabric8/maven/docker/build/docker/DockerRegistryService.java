package io.fabric8.maven.docker.build.docker;

import java.io.IOException;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.build.RegistryContext;
import io.fabric8.maven.docker.build.RegistryService;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.ImagePullPolicy;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;

/**
 * Allows to interact with registries, eg. to push/pull images.
 */
public class DockerRegistryService implements RegistryService {

    private final DockerAccess docker;
    private final Logger log;
    private final ImagePullCache imagePullCache;

    public DockerRegistryService(DockerAccess docker, Logger log, ImagePullCache.Backend backend) {
        this.docker = docker;
        this.log = log;
        this.imagePullCache = new ImagePullCache(backend);
    }

    /**
     * Push a set of images to a registry
     *
     * @param imageConfig image to push but only if it has a build configuration
     * @param retries how often to retry
     * @param skipTag flag to skip pushing tagged images
     * @throws DockerAccessException
     */
    @Override
    public void pushImage(ImageConfiguration imageConfig,
                          int retries, boolean skipTag, RegistryContext context) throws IOException {
        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
        String name = imageConfig.getName();
        if (buildConfig != null) {
            String registry = EnvUtil.firstRegistryOf(
                new ImageName(imageConfig.getName()).getRegistry(),
                imageConfig.getRegistry(),
                context.getPushRegistry());


            AuthConfig authConfig = context.lookupRegistryAuthConfig(true, new ImageName(name).getUser(), registry);

            long start = System.currentTimeMillis();
            docker.pushImage(name, authConfig, registry, retries);
            log.info("Pushed %s in %s", name, EnvUtil.formatDurationTill(start));

            if (!skipTag) {
                for (String tag : imageConfig.getBuildConfiguration().getTags()) {
                    if (tag != null) {
                        docker.pushImage(new ImageName(name, tag).getFullName(), authConfig, registry, retries);
                    }
                }
            }
        }
    }


    /**
     * Check an image, and, if <code>autoPull</code> is set to true, fetch it. Otherwise if the image
     * is not existent, throw an error
     *
     * @throws DockerAccessException
     */
    public void pullImage(String image, ImagePullPolicy policy, RegistryContext registryContext)
        throws DockerAccessException {

        // Already pulled, so we don't need to take care
        if (imagePullCache.hasAlreadyPulled(image)) {
            return;
        }

        // Check if a pull is required
        if (!imageRequiresPull(docker.hasImage(image), policy, image)) {
            return;
        }

        ImageName imageName = new ImageName(image);
        long time = System.currentTimeMillis();
        String registry = EnvUtil.firstRegistryOf(
            imageName.getRegistry(),
            registryContext.getPullRegistry());

        docker.pullImage(imageName.getFullName(),
                         registryContext.lookupRegistryAuthConfig(false, null, registry),
                         registry);
        log.info("Pulled %s in %s", imageName.getFullName(), EnvUtil.formatDurationTill(time));
        imagePullCache.pulled(image);

        if (registry != null && !imageName.hasRegistry()) {
            // If coming from a registry which was not contained in the original name, add a tag from the
            // full name with the registry to the short name with no-registry.
            docker.tag(imageName.getFullName(registry), image, false);
        }
    }


    // ============================================================================================================


    private boolean imageRequiresPull(boolean hasImage, ImagePullPolicy pullPolicy, String imageName) {

        // The logic here is like this (see also #96):
        // otherwise: don't pull

        if (pullPolicy == ImagePullPolicy.Never) {
            if (!hasImage) {
                throw new IllegalArgumentException(
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

}
