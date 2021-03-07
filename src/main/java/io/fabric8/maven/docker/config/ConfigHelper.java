package io.fabric8.maven.docker.config;
/*
 *
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.*;

import io.fabric8.maven.docker.config.handler.property.PropertyConfigHandler;
import io.fabric8.maven.docker.config.handler.property.PropertyMode;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.StringUtils;

/**
 * Utility class which helps in resolving, customizing, initializing and validating
 * image configuration.
 *
 * @author roland
 * @since 17/05/16
 */
public class ConfigHelper {
    // Property which can be set to activate externalConfiguration through properties.
    // Only works for single image project.
    public static final String EXTERNALCONFIG_ACTIVATION_PROPERTY = "docker.imagePropertyConfiguration";

    private ConfigHelper() {}

    /**
     * Resolve image with an external image resolver
     *
     * @param images the original image config list (can be null)
     * @param imageResolver the resolver used to extend on an image configuration
     * @param imageNameFilter filter to select only certain image configurations with the given name
     * @param imageCustomizer final customization hook for mangling the configuration
     * @return a list of resolved and customized image configuration.
     */
    public static List<ImageConfiguration> resolveImages(Logger logger,
                                                         List<ImageConfiguration> images,
                                                         Resolver imageResolver,
                                                         String imageNameFilter,
                                                         Customizer imageCustomizer) {
        List<ImageConfiguration> ret = resolveConfiguration(imageResolver, images);
        ret = imageCustomizer.customizeConfig(ret);
        List<ImageConfiguration> filtered =  filterImages(imageNameFilter,ret);
        if (ret.size() > 0 && filtered.size() == 0 && imageNameFilter != null) {
            List<String> imageNames = new ArrayList<>();
            for (ImageConfiguration image : ret) {
                imageNames.add(image.getName());
            }
            logger.warn("None of the resolved images [%s] match the configured filter '%s'",
                        StringUtils.join(imageNames.iterator(), ","), imageNameFilter);
        }
        return filtered;
    }

    public static void validateExternalPropertyActivation(MavenProject project, List<ImageConfiguration> images) throws MojoFailureException {
        String prop = getExternalConfigActivationProperty(project);
        if(prop == null) {
            return;
        }

        if(images.size() == 1) {
            return;
        }

        // With more than one image, externally activating propertyConfig get's tricky. We can only allow it to affect
        // one single image. Go through each image and check if they will be controlled by default properties.
        // If more than one image matches, fail.
        int imagesWithoutExternalConfig = 0;
        for (ImageConfiguration image : images) {
            if(PropertyConfigHandler.canCoexistWithOtherPropertyConfiguredImages(image.getExternalConfig())) {
                continue;
            }

            // else, it will be affected by the external property.
            imagesWithoutExternalConfig++;
        }

        if(imagesWithoutExternalConfig > 1) {
            throw new MojoFailureException("Configuration error: Cannot use property "+EXTERNALCONFIG_ACTIVATION_PROPERTY+" on projects with multiple images without explicit image external configuration.");
        }
    }

    public static String getExternalConfigActivationProperty(MavenProject project) {
        Properties properties = EnvUtil.getPropertiesWithSystemOverrides(project);
        String value = properties.getProperty(EXTERNALCONFIG_ACTIVATION_PROPERTY);

        // This can be used to disable in a more "local" context, if set globally
        if(PropertyMode.Skip.name().equalsIgnoreCase(value)) {
            return null;
        }

        return value;
    }

    /**
     * Initialize and validate the configuration.
     *
     *
     * @param images the images to check
     * @param apiVersion the original API version intended to use
     * @param nameFormatter formatter for image names
     * @param log a logger for printing out diagnostic messages
     * @return the minimal API Docker API required to be used for the given configuration.
     */
    public static String initAndValidate(List<ImageConfiguration> images, String apiVersion, NameFormatter nameFormatter,
                                         Logger log) {
        // Init and validate configs. After this step, getResolvedImages() contains the valid configuration.
        for (ImageConfiguration imageConfiguration : images) {
            apiVersion = EnvUtil.extractLargerVersion(apiVersion, imageConfiguration.initAndValidate(nameFormatter, log));
        }
        return apiVersion;
    }

    // Check if the provided image configuration matches the given
    public static boolean matchesConfiguredImages(String imageList, ImageConfiguration imageConfig) {
        if (imageList == null) {
            return true;
        }
        Set<String> imagesAllowed = new HashSet<>(Arrays.asList(imageList.split("\\s*,\\s*")));
        return imagesAllowed.contains(imageConfig.getName()) || imagesAllowed.contains(imageConfig.getAlias());
    }

    // ===========================================================================================================

    // Filter image configuration on name. Given filter should be either null (no filter) or a comma separated
    // list of image names which should be used
    private static List<ImageConfiguration> filterImages(String nameFilter, List<ImageConfiguration> imagesToFilter) {
        List<ImageConfiguration> ret = new ArrayList<>();
        for (ImageConfiguration imageConfig : imagesToFilter) {
            if (matchesConfiguredImages(nameFilter, imageConfig)) {
                ret.add(imageConfig);
            }
        }
        return ret;
    }

    // Resolve and initialize external configuration
    private static List<ImageConfiguration> resolveConfiguration(Resolver imageResolver,
                                                                 List<ImageConfiguration> unresolvedImages) {
        List<ImageConfiguration> ret = new ArrayList<>();
        if (unresolvedImages != null) {
            for (ImageConfiguration image : unresolvedImages) {
                ret.addAll(imageResolver.resolve(image));
            }
            verifyImageNames(ret);
        }
        return ret;
    }


    // Extract authentication information
    private static void verifyImageNames(List<ImageConfiguration> ret) {
        for (ImageConfiguration config : ret) {
            if (config.getName() == null) {
                throw new IllegalArgumentException("Configuration error: <image> must have a non-null <name>");
            }
        }
    }


    // =========================================================================

    /**
     * Allow subclasses to customize the given set of image configurations. This is called
     * after resolving of images. a customizer is free to change the image configuration as he want.
     * Use this with responsibility.
     */
    public interface Customizer {
        List<ImageConfiguration> customizeConfig(List<ImageConfiguration> configs);
    }

    /**
     * A resolver can map one given image configuration to one or more image configurations
     * This is e.g. used for resolving properties
     */
    public interface Resolver {
        List<ImageConfiguration> resolve(ImageConfiguration image);
    }

    /**
     * Format an image name by replacing certain placeholders
     */
    public interface NameFormatter {
        String format(String name);

        NameFormatter IDENTITY = new NameFormatter() {
            public String format(String name) {
                return name;
            }
        };
    }


}
