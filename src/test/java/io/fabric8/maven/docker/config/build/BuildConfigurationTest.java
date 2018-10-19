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

import java.io.File;

import io.fabric8.maven.docker.config.build.BuildConfiguration;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.Logger;
import mockit.Mocked;
import org.junit.Test;

import static io.fabric8.maven.docker.config.build.ArchiveCompression.bzip2;
import static io.fabric8.maven.docker.config.build.ArchiveCompression.gzip;
import static io.fabric8.maven.docker.config.build.ArchiveCompression.none;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author roland
 * @since 04/04/16
 */

public class BuildConfigurationTest {

    @Mocked
    Logger logger;

    @Test
    public void empty() {
        BuildConfiguration config = new BuildConfiguration();
        config.validate();
        assertFalse(config.isDockerFileMode());
    }

    @Test
    public void simpleDockerfile() {
        BuildConfiguration config =
            new BuildConfiguration.Builder().
                dockerFile("src/docker/Dockerfile").build();
        config.validate();
        assertTrue(config.isDockerFileMode());
        assertEquals(config.calculateDockerFilePath(),new File("src/docker/Dockerfile"));
    }

    @Test
    public void simpleDockerfileDir() {
        BuildConfiguration config =
            new BuildConfiguration.Builder().
                                                     contextDir("src/docker/").build();
        config.validate();
        assertTrue(config.isDockerFileMode());
        assertEquals(config.calculateDockerFilePath(),new File("src/docker/Dockerfile"));
    }

    @Test
    public void DockerfileDirAndDockerfileAlsoSet() {
        BuildConfiguration config =
            new BuildConfiguration.Builder().
                                                     contextDir("/tmp/").
                dockerFile("Dockerfile").build();
        config.validate();
        assertTrue(config.isDockerFileMode());
        assertEquals(config.calculateDockerFilePath(),new File("/tmp/Dockerfile"));
    }

    @Test
    public void dockerFileAndArchive() {
        BuildConfiguration config =
            new BuildConfiguration.Builder().
                dockerArchive("this").
                dockerFile("that").build();

        try {
            config.validate();
        } catch (IllegalArgumentException expected) {
            return;
        }
        fail("Should have failed.");
    }

    @Test
    public void dockerArchive() {
        BuildConfiguration config =
            new BuildConfiguration.Builder().
                dockerArchive("this").build();
        config.validate();

        assertFalse(config.isDockerFileMode());
        assertEquals("this", config.getDockerArchive());
    }

    @Test
    public void compression() {
        BuildConfiguration config =
            new BuildConfiguration.Builder().
                compression("gzip").build();
        assertEquals(gzip, config.getCompression());

        config = new BuildConfiguration.Builder().build();
        assertEquals(none, config.getCompression());

        config =
            new BuildConfiguration.Builder().
                compression("bzip2").build();
        assertEquals(bzip2, config.getCompression());

        try {
            new BuildConfiguration.Builder().
                compression("bzip").build();
            fail();
        } catch (Exception exp) {
            assertTrue(exp.getMessage().contains("bzip"));
        }
    }


    @Test
    public void isValidWindowsFileName() {
        BuildConfiguration cfg = new BuildConfiguration();
    	assertFalse(cfg.isValidWindowsFileName("/Dockerfile"));
    	assertTrue(cfg.isValidWindowsFileName("Dockerfile"));
    	assertFalse(cfg.isValidWindowsFileName("Dockerfile/"));
    }


}
