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

import java.io.File;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenReaderFilter;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.assembly.DockerAssemblyManager;
import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.util.ImageName;
import org.jolokia.docker.maven.util.MojoParameters;

/**
 * @author roland
 * @since 26/06/15
 */
abstract public class AbstractBuildSupporMojo extends AbstractDockerMojo {
    // ==============================================================================================================
    // Parameters required from Maven when building an assembly. They cannot be injected directly
    // into DockerAssemblyCreator.
    // See also here: http://maven.40175.n5.nabble.com/Mojo-Java-1-5-Component-MavenProject-returns-null-vs-JavaDoc-parameter-expression-quot-project-quot-s-td5733805.html
    /** @parameter */
    private MavenArchiveConfiguration archive;

    /** @component */
    protected MavenSession session;

    /** @component */
    private MavenFileFilter mavenFileFilter;

    /** @component */
    private MavenReaderFilter mavenFilterReader;

   
    /**
     * @parameter default-value="src/main/docker" property="docker.source.dir"
     */
    private String sourceDirectory;

    /**
     * @parameter default-value="target/docker" property="docker.target.dir"
     */
    private String outputDirectory;


    protected MojoParameters createMojoParameters() {
        return new MojoParameters(session, project, archive, mavenFileFilter, mavenFilterReader,
                                  sourceDirectory, outputDirectory);
    }

    protected void buildImage(DockerAccess dockerAccess, String imageName, ImageConfiguration imageConfig)
            throws DockerAccessException, MojoExecutionException {
        warnIfDeprecatedCommandConfigIsUsed(imageConfig.getBuildConfiguration());

        autoPullBaseImage(dockerAccess, imageConfig);

        MojoParameters params = createMojoParameters();
        serviceHub.getBuildService().buildImage(imageConfig, params);
    }

    private void autoPullBaseImage(DockerAccess dockerAccess, ImageConfiguration imageConfig)
            throws DockerAccessException, MojoExecutionException {
        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
        String fromImage = buildConfig.getFrom();
        if (fromImage == null) {
            AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();
            if (assemblyConfig == null || assemblyConfig.getDockerFileDir() == null) {
                fromImage = DockerAssemblyManager.DEFAULT_DATA_BASE_IMAGE;
            }
        }
        if (fromImage != null) {
            checkImageWithAutoPull(dockerAccess, fromImage, new ImageName(fromImage).getRegistry(),true);
        }
    }

    private void warnIfDeprecatedCommandConfigIsUsed(BuildImageConfiguration buildConfiguration) {
        if (buildConfiguration.getCommand() != null) {
            log.warn("<command> in the <build> configuration is deprecated and will be be removed soon");
            log.warn("Please use <cmd> with nested <shell> or <exec> sections instead.");
            log.warn("");
            log.warn("More on this is explained in the user manual: ");
            log.warn("https://github.com/rhuss/docker-maven-plugin/blob/master/doc/manual.md#start-up-arguments");
            log.warn("");
            log.warn("Migration is trivial, see changelog to version 0.12.0 -->");
            log.warn("https://github.com/rhuss/docker-maven-plugin/blob/master/doc/changelog.md");
            log.warn("");
            log.warn("For now, the command is automatically translated for you to the shell form:");
            log.warn("   <cmd><shell>" + buildConfiguration.getCommand() + "</shell></cmd>");
        }
    }
}
