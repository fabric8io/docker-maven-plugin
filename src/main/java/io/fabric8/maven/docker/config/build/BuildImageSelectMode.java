package io.fabric8.maven.docker.config.build;
/*
 *
 * Copyright 2016 Roland Huss
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
 * How to build source tar files.
 *
 * @author roland
 * @since 26/04/16
 */
public enum BuildImageSelectMode {

    // Pick only the first build configuration
    first,

    // Include all builds with alias names as classifiers
    all;

}
