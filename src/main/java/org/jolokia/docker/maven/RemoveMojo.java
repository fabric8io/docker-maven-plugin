package org.jolokia.docker.maven;/*
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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.config.ImageConfiguration;

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
 */

@Mojo(name = "remove", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class RemoveMojo extends AbstractDockerMojo {

    // Whether all configured images should be removed
    @Parameter(property = "docker.removeAll", defaultValue = "false")
    private boolean removeAll;

    @Override
    protected void executeInternal(DockerAccess dockerAccess) throws DockerAccessException, MojoExecutionException {
        for (ImageConfiguration image : getImages()) {
            String name = image.getName();
            if (removeAll || image.isDataImage()) {
                if (dockerAccess.hasImage(name)) {
                    if (dockerAccess.removeImage(name,true)) {
                        info("Removed image " + getImageDescription(name, image.getAlias()));
                    }
                }
            }
        }
    }
}
