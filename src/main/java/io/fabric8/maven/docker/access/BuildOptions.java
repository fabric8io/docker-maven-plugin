package io.fabric8.maven.docker.access;/*
 *
 * Copyright 2015-2016 Red Hat, Inc.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.maven.docker.util.JsonFactory;

/**
 * @author roland
 * @since 03/01/17
 */
public class BuildOptions {

    private Map<String, String> options;

    public BuildOptions() {
        this(new HashMap<String, String>());
    }

    public BuildOptions(Map<String, String> options) {
        this.options = options != null ? new HashMap<>(options) : new HashMap<String, String>();
    }

    public BuildOptions addOption(String key, String value) {
        options.put(key,value);
        return this;
    }

    public BuildOptions dockerfile(String name) {
        if (name != null) {
            options.put("dockerfile", name);
        }
        return this;
    }

    public BuildOptions forceRemove(boolean forceRm) {
        if (forceRm) {
            options.put("forcerm", "1");
        }
        return this;
    }

    public BuildOptions noCache(boolean noCache) {
        options.put("nocache", noCache ? "1" : "0");
        return this;
    }

    public BuildOptions cacheFrom(List<String> cacheFrom) {
        if (cacheFrom == null || cacheFrom.isEmpty()) {
            options.remove("cachefrom");
        } else {
            options.put("cachefrom", JsonFactory.newJsonArray(cacheFrom).toString());
        }
        return this;
    }

    public BuildOptions buildArgs(Map<String, String> buildArgs) {
        if (buildArgs != null && buildArgs.size() > 0) {
            options.put("buildargs", JsonFactory.newJsonObject(buildArgs).toString());
        }
        return this;
    }

    public Map<String, String> getOptions() {
        return options;
    }
}

