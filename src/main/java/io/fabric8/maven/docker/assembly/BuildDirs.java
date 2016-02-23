package io.fabric8.maven.docker.assembly;/*
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

import io.fabric8.maven.docker.util.MojoParameters;
import io.fabric8.maven.docker.util.EnvUtil;

/**
 * Helper object grouping together all working and output
 * directories.
 *
 * @author roland
 * @since 27/02/15
 */
class BuildDirs {

    private final String buildTopDir;
    private final MojoParameters params;

    /**
     * Constructor building up the the output directories
     *
     * @param imageName image name for the image to build
     * @param params mojo params holding base and global outptput dir
     */
    BuildDirs(String imageName, MojoParameters params) {
        this.params = params;
        // Replace tag separator with a slash to avoid problems
        // with OSs which gets confused by colons.
        this.buildTopDir = imageName != null ? imageName.replace(':', '/') : null;
    }

    File getOutputDirectory() {
        return getDir("build");
    }

    File getWorkingDirectory() {
        return getDir("work");
    }

    File getTemporaryRootDirectory() {
        return getDir("tmp");
    }

    void createDirs() {
        for (String workDir : new String[] { "build", "work", "tmp" }) {
            File dir = getDir(workDir);
            if (!dir.exists()) {
                if(!dir.mkdirs()) {
                    throw new IllegalArgumentException("Cannot create directory " + dir.getAbsolutePath());
                }
            }
        }
    }

    private File getDir(String dir) {
        return EnvUtil.prepareAbsoluteOutputDirPath(params, buildTopDir, dir);
    }
}
