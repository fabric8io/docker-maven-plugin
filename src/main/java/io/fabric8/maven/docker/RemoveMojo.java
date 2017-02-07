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

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Mojo for removing images. By default only data images are removed. Data images are
 * images without a run configuration.
 *
 * By setting <code>removeAll</code> (property: <code>docker.removeAll</code>) also other
 * images can be removed.
 *
 * In order to explicitely restrict the images to remove, use <code>images</code> to specify a
 * (comma separated) list of images to remove.
 *
 * @author roland
 * @since 23.10.14
 *
 */
@Mojo(name = "remove", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class RemoveMojo extends AbstractDockerMojo {

    // Should all configured images should be removed?
    @Parameter(property = "docker.removeAll", defaultValue = "false")
    private boolean removeAll;

    @Override
    protected void executeInternal(ServiceHub hub) throws DockerAccessException {
        QueryService queryService = hub.getQueryService();

        for (ImageConfiguration image : getResolvedImages()) {
            String name = image.getName();
            if (removeAll || image.isDataImage()) {
                if (queryService.hasImage(name)) {
                    if (hub.getDockerAccess().removeImage(name,true)) {
                        log.info("%s: Remove",image.getDescription());
                    }
                }
            }
        }
    }
}
