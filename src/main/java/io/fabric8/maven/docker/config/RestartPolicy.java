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

import org.apache.maven.plugins.annotations.Parameter;

/**
* @author roland
* @since 08/12/14
*/
public class RestartPolicy implements Serializable {

    public static final RestartPolicy DEFAULT = new RestartPolicy();

    @Parameter
    private String name;

    @Parameter
    private int retry;

    public RestartPolicy() {};

    public String getName() {
        return name;
    }

    public int getRetry() {
        return retry;
    }

    // ================================================

    public static class Builder {

        private RestartPolicy policy = new RestartPolicy();

        public Builder name(String name) {
            policy.name = name;
            return this;
        }

        public Builder retry(int retry) {
            policy.retry = retry;
            return this;
        }

        public RestartPolicy build() {
            return policy;
        }
    }
}
