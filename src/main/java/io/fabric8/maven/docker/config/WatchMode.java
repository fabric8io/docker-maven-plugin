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
 * How to watch for image changes
 * @author roland
 * @since 16/06/15
 */
public enum WatchMode {

    /**
     * Copy watched artefacts into contaienr
     */
    copy(false, false, true, "build"),

    /**
     * Build only images
     */
    build(true, false, false, "build"),

    /**
     * Run images
     */
    run(false, true, false, "run"),

    /**
     * Build and run images
     */
    both(true, true, false, "build and run"),

    /**
     * Neither build nor run
     */
    none(false, false, false, "no build and no run");

    private final boolean doRun;
    private final boolean doBuild;
    private final boolean doCopy;
    private final String description;

    WatchMode(boolean doBuild, boolean doRun, boolean doCopy, String description) {
        this.doBuild = doBuild;
        this.doRun = doRun;
        this.doCopy = doCopy;
        this.description = description;
    }

    public boolean isRun() {
        return doRun;
    }

    public boolean isBuild() {
        return doBuild;
    }

    public boolean isCopy() {
        return doCopy;
    }

    public String getDescription() {
        return description;
    }
}
