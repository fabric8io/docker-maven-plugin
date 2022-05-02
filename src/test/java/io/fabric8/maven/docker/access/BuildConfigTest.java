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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import io.fabric8.maven.docker.util.JsonFactory;

/**
 * @author roland
 * @since 03/01/17
 */
class BuildConfigTest {

    @Test
    void empty() {
        BuildOptions opts = new BuildOptions();
        Assertions.assertEquals(0, opts.getOptions().size());
    }

    @Test
    void forcerm() {
        BuildOptions opts = new BuildOptions().forceRemove(false);
        Assertions.assertEquals(0, opts.getOptions().size());
        opts = new BuildOptions().forceRemove(true);
        Assertions.assertEquals("1", opts.getOptions().get("forcerm"));
    }

    @Test
    void nocache() {
        BuildOptions opts = new BuildOptions().noCache(true);
        Assertions.assertEquals("1", opts.getOptions().get("nocache"));
        opts = new BuildOptions().noCache(false);
        Assertions.assertEquals("0", opts.getOptions().get("nocache"));
    }

    @Test
    void squash() {
        BuildOptions opts = new BuildOptions().squash(true);
        Assertions.assertEquals(1, opts.getOptions().size());
        Assertions.assertEquals("1", opts.getOptions().get("squash"));
        opts.squash(false);
        Assertions.assertEquals("0", opts.getOptions().get("squash"));
        opts.addOption("squash","1");
        Assertions.assertEquals("1", opts.getOptions().get("squash"));
        Assertions.assertEquals(1, opts.getOptions().size());
    }

    @Test
    void dockerfile() {
        BuildOptions opts = new BuildOptions().dockerfile("blub");
        Assertions.assertEquals("blub", opts.getOptions().get("dockerfile"));
        opts = new BuildOptions().dockerfile(null);
        Assertions.assertEquals(0, opts.getOptions().size());
    }

    @Test
    void buildArgs() {
        Map<String,String> args = Collections.singletonMap("arg1","blub");
        BuildOptions opts = new BuildOptions().buildArgs(args);
        Assertions.assertEquals(JsonFactory.newJsonObject(args).toString(), opts.getOptions().get("buildargs"));
        opts = new BuildOptions().buildArgs(null);
        Assertions.assertEquals(0, opts.getOptions().size());

    }

    @Test
    void override() {
        BuildOptions opts = new BuildOptions(Collections.singletonMap("nocache","1"));
        Assertions.assertEquals(1, opts.getOptions().size());
        Assertions.assertEquals("1", opts.getOptions().get("nocache"));
        opts.noCache(false);
        Assertions.assertEquals("0", opts.getOptions().get("nocache"));
        opts.addOption("nocache","1");
        Assertions.assertEquals("1", opts.getOptions().get("nocache"));
    }

    @Test
    void cacheFrom() {
        BuildOptions opts = new BuildOptions().cacheFrom(Arrays.asList("foo/bar:latest"));
        Assertions.assertEquals("[\"foo/bar:latest\"]", opts.getOptions().get("cachefrom"));

        opts.cacheFrom(Arrays.asList("foo/bar:latest", "foo/baz:1.0"));
        Assertions.assertEquals("[\"foo/bar:latest\",\"foo/baz:1.0\"]", opts.getOptions().get("cachefrom"));

        opts.cacheFrom(Arrays.asList());
        Assertions.assertEquals(null, opts.getOptions().get("cachefrom"));

        opts.cacheFrom(null);
        Assertions.assertEquals(null, opts.getOptions().get("cachefrom"));
    }

    @Test
    void network() {
        BuildOptions opts = new BuildOptions().network(null);
        Assertions.assertEquals(null, opts.getOptions().get("networkmode"));

        opts.network("host");
        Assertions.assertEquals("host", opts.getOptions().get("networkmode"));
    }
}
