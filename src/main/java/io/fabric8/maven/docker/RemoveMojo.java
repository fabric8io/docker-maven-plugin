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

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.Collections;
import java.util.List;

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
    
    @Override
    protected void executeInternal(ServiceHub hub) throws DockerAccessException {
        for (ImageConfiguration image : getResolvedImages()) {
            String name = image.getName();

            if (imageShouldBeRemoved(image)) {
                removeImage(hub, name);

                if(!skipTag) {
                    // Remove any tagged images
                    for (String tag: getImageBuildTags(image)){
                        removeImage(hub, new ImageName(name, tag).getFullName());
                    }
                }
            }
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
            return image.getRegistry() != null;
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
