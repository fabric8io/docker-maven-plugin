package io.fabric8.maven.docker;/*
 *
 * Copyright 2014 Roland Huss
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

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.QueryService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.NamePatternUtil;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Mojo for removing images. By default only data images are removed. Data images are
 * images without a run configuration.
 *
 * By setting <code>removeAll</code> (property: <code>docker.removeAll</code>) also other
 * images can be removed.
 *
 * In order to explicitly restrict the images to remove, use <code>images</code> to specify a
 * (comma separated) list of images to remove.
 *
 * @author roland
 * @since 23.10.14
 *
 */
@Mojo(name = "remove", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class RemoveMojo extends AbstractDockerMojo {

    // Should all configured images should be removed?
    @Parameter(property = "docker.removeAll")
    @Deprecated
    private Boolean removeAll;

    @Parameter(property = "docker.removeMode")
    private String removeMode;
    
    /** 
     * Skip building tags
     */
    @Parameter(property = "docker.skip.tag", defaultValue = "false")
    private boolean skipTag;

    @Parameter(property = "docker.removeNamePattern")
    private String removeNamePattern;

    @Override
    protected void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        for (ImageConfiguration image : getResolvedImages()) {
            if (imageShouldBeRemoved(image)) {
                for(String name : getImageNamesToRemoveForImage(hub, image)) {
                    removeImage(hub, name);
                }

                if(!skipTag) {
                    // Remove any tagged images
                    for (String tag: getImageBuildTags(image)){
                        removeImage(hub, new ImageName(image.getName(), tag).getFullName());
                    }
                }
            }
        }

        // If a global pattern option is provided, process it after per-image patterns
        for(String name : getImageNamesToRemoveForMojo(hub)) {
            removeImage(hub, name);
        }
    }

    private boolean imageShouldBeRemoved(ImageConfiguration image) {
        if ("all".equalsIgnoreCase(removeMode)) {
            return true;
        }
        if ("build".equalsIgnoreCase(removeMode)) {
            return image.getBuildConfiguration() != null;
        }
        if ("run".equalsIgnoreCase(removeMode)) {
            return image.getBuildConfiguration() == null;
        }
        if ("data".equalsIgnoreCase(removeMode)) {
            return image.isDataImage();
        }
        if (removeAll != null) {
            return removeAll || image.isDataImage();
        }
        // Default
        return image.getBuildConfiguration() != null;
    }

    private Collection<String> getImageNamesMatchingPattern(ServiceHub hub, Matcher imageNameMatcher)
            throws MojoExecutionException, DockerAccessException {
        return hub.getQueryService().listImages(false)
                .stream()
                .flatMap(image -> image.getRepoTags().stream())
                .filter(repoTag -> imageNameMatcher.reset(repoTag).matches())
                .collect(Collectors.toList());
    }

    private Collection<String> getImageNamesToRemoveForMojo(ServiceHub hub)
            throws MojoExecutionException, DockerAccessException {
        if(removeNamePattern != null) {
            Matcher imageNameMatcher = getImageNameMatcher(removeNamePattern);

            if(imageNameMatcher == null) {
                log.warn("There are no image name patterns in removeNamePattern for docker:remove");
                return Collections.emptyList();
            }

            return getImageNamesMatchingPattern(hub, imageNameMatcher);
        }

        return Collections.emptyList();
    }

    private Collection<String> getImageNamesToRemoveForImage(ServiceHub hub, ImageConfiguration imageConfiguration)
            throws MojoExecutionException, DockerAccessException {

        if(imageConfiguration.getRemoveNamePattern() != null) {
            Matcher imageNameMatcher = getImageNameMatcher(imageConfiguration.getRemoveNamePattern());

            if(imageNameMatcher == null) {
                log.warn("There are no image name patterns in removeNamePattern for image %s: no images will be removed", imageConfiguration.getName());
                return Collections.emptyList();
            }

            return getImageNamesMatchingPattern(hub, imageNameMatcher);
        }

        return Collections.singleton(imageConfiguration.getName());
    }

    private Matcher getImageNameMatcher(String removeNamePattern) throws MojoExecutionException {
        try {
            String imageNameRegex = NamePatternUtil.convertNamePatternList(removeNamePattern, NamePatternUtil.IMAGE_FIELD, true);
            if(imageNameRegex == null) {
                log.debug("No image name patterns in removeNamePattern %s", removeNamePattern);
                return null;
            } else {
                log.debug("Converted removeNamePattern %s into image name regular expression %s", removeNamePattern, imageNameRegex);
                return Pattern.compile(imageNameRegex).matcher("");
            }
        } catch(IllegalArgumentException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void removeImage(ServiceHub hub, String name) throws DockerAccessException {
        QueryService queryService = hub.getQueryService();
        if (queryService.hasImage(name)) {
            if (hub.getDockerAccess().removeImage(name,true)) {
                log.info("%s: Remove", name);
            }
        }
    }

    private List<String> getImageBuildTags(ImageConfiguration image){
        return image.getBuildConfiguration() != null ?
            image.getBuildConfiguration().getTags() :
            Collections.<String>emptyList();
    }
}
