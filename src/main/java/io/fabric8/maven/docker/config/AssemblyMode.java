package io.fabric8.maven.docker.config;/*
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

/**
 * Various modes how to add file for the tarball for "docker:build".
 *
 * @author roland
 * @since 18/05/15
 */
public enum AssemblyMode {

    /**
     * Copy files directly in the directory
     */
    dir("dir",false),

    /**
     * Use a ZIP container as intermediate format
     */
    zip("zip",true),

    /**
     * Use a TAR container as intermediate format
     */
    tar("tar",true),

    /**
     * Use a compressed TAR container as intermediate format
     */
    tgz("tgz",true);

    private final String extension;
    private boolean isArchive;

    AssemblyMode(String extension, boolean isArchive) {
        this.extension = extension;
        this.isArchive = isArchive;
    }

    /**
     * Get the extension as known by the Maven assembler
     *
     * @return extension
     */
    public String getExtension() {
        return extension;
    }

    public boolean isArchive() {
        return isArchive;
    }
}
