package io.fabric8.maven.docker;/*
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
import java.util.ArrayList;
import java.util.List;

import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.SourceMode;
import io.fabric8.maven.docker.util.MojoParameters;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProjectHelper;

/**
 * Mojo for attaching one more source docker tar file to an artifact.
 *
 * If used in a lifecycle called 'docker-tar' the artifacts are attached
 * without classifier. Otherwise the classifier is "docker" and, depending on the
 * selected {@link SourceMode}, the alias name of the build configuration.
 *
 * @author roland
 * @since 25/10/15
 */
@Mojo(name = "source", defaultPhase = LifecyclePhase.PACKAGE)
@Execute(phase = LifecyclePhase.PACKAGE)
public class SourceMojo extends  AbstractBuildSupportMojo {

    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Mode how to attach the source artifact:
     *
     * <ul>
     *     <li><strong>first</strong> : The first image with build configuration</li>
     *     <li><strong>all</strong> : All images with build configuration. Each image must have an alias
     *        configured which is use as part of the classifier.
     *     </li>
     * </ul>
     *
     * Use this ...
     */
    @Parameter
    private SourceMode sourceMode = SourceMode.first;

    @Override
    protected void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        MojoParameters params = createMojoParameters();
        List<ImageConfiguration> imageConfigs = new ArrayList<>();
        for (ImageConfiguration imageConfig : getResolvedImages()) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            if (buildConfig != null) {
                if (buildConfig.skip()) {
                    log.info("%s: Skipped creating source",imageConfig.getDescription());
                } else {
                    imageConfigs.add(imageConfig);
                }
            }
        }
        if (sourceMode == SourceMode.first && imageConfigs.size() > 0) {
            ImageConfiguration imageConfig = imageConfigs.get(0);
            File dockerTar = hub.getArchiveService().createDockerBuildArchive(imageConfig, params);
            projectHelper.attachArtifact(project, getArchiveType(imageConfig),
                                         getClassifier(null), dockerTar);
        } else {
            for (ImageConfiguration imageConfig : imageConfigs) {
                File dockerTar = hub.getArchiveService().createDockerBuildArchive(imageConfig, params);
                String alias = imageConfig.getAlias();
                if (alias == null) {
                    throw new IllegalArgumentException(
                        "Image " + imageConfig.getDescription() + " must have an 'alias' configured to be " +
                        "used as a classifier for attaching a docker build tar as source to the maven build");
                } else {
                    projectHelper.attachArtifact(project, getArchiveType(imageConfig), getClassifier(alias), dockerTar);
                }
            }
        }
    }

    private String getClassifier(String alias) {
        String packaging = project.getPackaging();
        if ("docker-tar".equalsIgnoreCase(packaging)) {
            return alias;
        } else {
            return "docker" + (alias != null ? "-" + alias : "");
        }
    }

    private String getArchiveType(ImageConfiguration imageConfig) {
        return imageConfig.getBuildConfiguration().getCompression().getFileSuffix();
    }

    @Override
    protected boolean isDockerAccessRequired() {
        // dont need a running docker host for creating the docker tar
        return false;
    }
}
