package org.jolokia.docker.maven;/*
 * 
 * Copyright 2015 Roland Huss
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

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProjectHelper;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.config.BuildImageConfiguration;
import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.service.ServiceHub;
import org.jolokia.docker.maven.util.MojoParameters;

/**
 * Mojo for attaching one more source docker tar file to an artifact.
 *
 * @goal source
 * @phase package
 * @execute phase="generate-sources"
 * @executionStrategy once-per-session
 *
 * @author roland
 * @since 25/10/15
 */
public class SourceMojo extends  AbstractBuildSupportMojo {

    /** @component */
    private MavenProjectHelper projectHelper;

    @Override
    protected void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        MojoParameters params = createMojoParameters();
        for (ImageConfiguration imageConfig : getImages()) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            if (buildConfig != null) {
                if (buildConfig.skip()) {
                    log.info(imageConfig.getDescription() + ": Skipped creating source");
                } else {
                    File dockerTar =
                            hub.getArchiveService().createDockerBuildArchive(imageConfig, params);
                    String alias = imageConfig.getAlias();
                    if (alias == null) {
                        throw new IllegalArgumentException(
                                "Image " + imageConfig.getDescription() + " must have an 'alias' configured to be " +
                                "used as a classifier for attaching a docker build tar as source to the maven build");
                    }
                    projectHelper.attachArtifact(project, buildConfig.getCompression().getFileSuffix(),"docker-" + alias, dockerTar);
                }
            }
        }
    }

    @Override
    protected boolean isDockerAccessRequired() {
        // dont need a running docker host for creating the docker tar
        return false;
    }
}
