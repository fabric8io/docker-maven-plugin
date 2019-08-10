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

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import io.fabric8.maven.docker.util.JsonFactory;

import static org.junit.Assert.assertEquals;

/**
 * @author roland
 * @since 03/01/17
 */
public class BuildConfigTest {

    @Test
    public void empty() {
        BuildOptions opts = new BuildOptions();
        assertEquals(0, opts.getOptions().size());
    }

    @Test
    public void forcerm() {
        BuildOptions opts = new BuildOptions().forceRemove(false);
        assertEquals(0, opts.getOptions().size());
        opts = new BuildOptions().forceRemove(true);
        assertEquals("1", opts.getOptions().get("forcerm"));
    }

    @Test
    public void nocache() {
        BuildOptions opts = new BuildOptions().noCache(true);
        assertEquals("1", opts.getOptions().get("nocache"));
        opts = new BuildOptions().noCache(false);
        assertEquals("0", opts.getOptions().get("nocache"));
    }

    @Test
    public void dockerfile() {
        BuildOptions opts = new BuildOptions().dockerfile("blub");
        assertEquals("blub", opts.getOptions().get("dockerfile"));
        opts = new BuildOptions().dockerfile(null);
        assertEquals(0, opts.getOptions().size());
    }

    @Test
    public void buildArgs() {
        Map<String,String> args = Collections.singletonMap("arg1","blub");
        BuildOptions opts = new BuildOptions().buildArgs(args);
        assertEquals(JsonFactory.newJsonObject(args).toString(), opts.getOptions().get("buildargs"));
        opts = new BuildOptions().buildArgs(null);
        assertEquals(0, opts.getOptions().size());

    }

    @Test
    public void override() {
        BuildOptions opts = new BuildOptions(Collections.singletonMap("nocache","1"));
        assertEquals(1, opts.getOptions().size());
        assertEquals("1", opts.getOptions().get("nocache"));
        opts.noCache(false);
        assertEquals("0", opts.getOptions().get("nocache"));
        opts.addOption("nocache","1");
        assertEquals("1", opts.getOptions().get("nocache"));
    }

    @Test
    public void cacheFrom() {
        BuildOptions opts = new BuildOptions().cacheFrom(Arrays.asList("foo/bar:latest"));
        assertEquals("[\"foo/bar:latest\"]", opts.getOptions().get("cachefrom"));

        opts.cacheFrom(Arrays.asList("foo/bar:latest", "foo/baz:1.0"));
        assertEquals("[\"foo/bar:latest\",\"foo/baz:1.0\"]", opts.getOptions().get("cachefrom"));

        opts.cacheFrom(Arrays.asList());
        assertEquals(null, opts.getOptions().get("cachefrom"));

        opts.cacheFrom(null);
        assertEquals(null, opts.getOptions().get("cachefrom"));
    }
}
