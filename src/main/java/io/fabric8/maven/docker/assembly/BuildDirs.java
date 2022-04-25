package io.fabric8.maven.docker.assembly;
/*
 *
 * Copyright 2014 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.fabric8.maven.docker.util.MojoParameters;

import javax.annotation.Nonnull;

/**
 * Helper object grouping together all working and output
 * directories.
 *
 * @author roland
 * @since 27/02/15
 */
public class BuildDirs {

    private final Path buildPath;
    private final String buildTopDir;

    /**
     * Constructor building up the output directories
     *
     * @param outputPath docker output
     * @param imageName image name for the image to build
     */
    public BuildDirs(@Nonnull Path outputPath, @Nonnull String imageName) {
        // Replace tag separator with a slash to avoid problems with OSs which get confused by colons.
        buildTopDir = imageName.replace(':', File.separatorChar).replace('/', File.separatorChar);
        buildPath = outputPath.resolve(buildTopDir);
    }

    /**
     * Constructor building up the output directories
     *
     * @param imageName image name for the image to build
     * @param params mojo params holding base and global output dir
     */
    public BuildDirs(@Nonnull String imageName, MojoParameters params) {
        this(params.getProject().getBasedir().toPath().resolve(params.getOutputDirectory()), imageName);
    }

    public String getBuildTopDir() {
        return buildTopDir;
    }

    public File getOutputDirectory() {
        return getDir("build");
    }

    public File getWorkingDirectory() {
        return getDir("work");
    }

    public File getTemporaryRootDirectory() {
        return getDir("tmp");
    }

    public Path getPath(String subdir) {
        return buildPath.resolve(subdir);
    }

    private File getDir(String subdir) {
        return getPath(subdir).toFile();
    }

    void createDirs() {
        for (String workDir : new String[] { "build", "work", "tmp" }) {
            Path dir = getPath(workDir);
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot create directory " + dir);
            }
        }
    }
}
