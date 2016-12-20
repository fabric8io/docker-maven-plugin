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

import java.io.Serializable;
import java.util.*;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Run configuration for volumes.
 *
 * @author roland
 * @since 08/12/14
 */
public class RunVolumeConfiguration implements Serializable {

    /**
     * List of images names from where volumes are mounted
     */
    @Parameter
    private List<String> from;

    /**
     * List of bind parameters for binding/mounting host directories
     * into the container
     */
    @Parameter
    private List<String> bind;

    /**
     * List of images to mount from
     *
     * @return images
     */
    public List<String> getFrom() {
        return from;
    }

    /**
     * List of docker bind specification for mounting local directories
     * @return list of bind specs
     */
    public List<String> getBind() {
        return bind;
    }

    // ===========================================

    public static class Builder {

        private RunVolumeConfiguration config = new RunVolumeConfiguration();

        public Builder() {
            this.config = new RunVolumeConfiguration();
        }

        public Builder from(List<String> args) {
            if (args != null) {
                if (config.from == null) {
                    config.from = new ArrayList<>();
                }
                config.from.addAll(args);
            }
            return this;
        }

        public Builder bind(List<String> args) {
            if (args != null) {
                if (config.bind == null) {
                    config.bind = new ArrayList<>();
                }
                config.bind.addAll(args);
            }
            return this;
        }

        public RunVolumeConfiguration build() {
            return config;
        }
    }
}
