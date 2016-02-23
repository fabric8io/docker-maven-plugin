package io.fabric8.maven.docker.util;/*
 * 
 * Copyright 2015 Roland Huss
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

import java.io.*;
import java.nio.file.Files;

import org.codehaus.plexus.util.IOUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author roland
 * @since 21/01/16
 */
public class DockerFileUtilTest {

    @Test
    public void testSimple() throws Exception {
        File toTest = copyToTempDir("Dockerfile_from_simple");
        assertEquals("fabric8/s2i-java",DockerFileUtil.extractBaseImage(toTest));
    }

    private File copyToTempDir(String resource) throws IOException {
        File dir = Files.createTempDirectory("d-m-p").toFile();
        File ret = new File(dir, "Dockerfile");
        try (FileOutputStream os = new FileOutputStream(ret)) {
            IOUtil.copy(getClass().getResourceAsStream(resource), os);
        }
        return ret;
    }
}
