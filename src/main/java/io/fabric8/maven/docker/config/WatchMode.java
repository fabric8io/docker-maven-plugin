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
    copy(false,false,true),

    /**
     * Build only images
     */
    build(true, false, false),

    /**
     * Run images
     */
    run(false, true, false),

    /**
     * Build and run images
     */
    both(true, true, false),

    /**
     * Neither build nor run
     */
    none(false, false, false);

    private final boolean doRun;
    private final boolean doBuild;
    private final boolean doCopy;

    WatchMode(boolean doBuild, boolean doRun, boolean doCopy) {
        this.doBuild = doBuild;
        this.doRun = doRun;
        this.doCopy = doCopy;
    }

    @Override
    public String toString() {
        return "WatchMode{" +
                "doRun=" + doRun +
                ", doBuild=" + doBuild +
                ", doCopy=" + doCopy +
                '}';
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
}
