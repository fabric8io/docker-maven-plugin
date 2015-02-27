package org.jolokia.docker.maven.assembly;/*
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

import org.jolokia.docker.maven.util.EnvUtil;
import org.jolokia.docker.maven.util.MojoParameters;

/**
 * Helper object grouping together all working and output
 * directories.
 *
 * @author roland
 * @since 27/02/15
 */
public class BuildDirs {

    private final String buildTopDir;
    private final MojoParameters params;

    /**
     * Constructor building up the the output directories
     *
     * @param params mojo params holding base and global outptput dir
     * @param imageName image name for the image to build
     */
    public BuildDirs(MojoParameters params, String imageName) {
        this.params = params;
        // Replace tag separator with a slash to avoid problems
        // with OSs which gets confused by colons.
        this.buildTopDir = imageName != null ? imageName.replace(':', '/') : null;
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

    public void createDirs() {
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
