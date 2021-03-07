package io.fabric8.maven.docker.config;
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
import java.io.IOException;

import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.Test;

import static io.fabric8.maven.docker.config.ArchiveCompression.gzip;
import static io.fabric8.maven.docker.config.ArchiveCompression.none;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author roland
 * @since 04/04/16
 */

public class BuildImageConfigurationTest {

    @Mocked
    Logger logger;

    @Test
    public void empty() {
        BuildImageConfiguration config = new BuildImageConfiguration();
        config.initAndValidate(logger);
        assertFalse(config.isDockerFileMode());
    }

    @Test
    public void simpleDockerfile() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                dockerFile("src/main/docker/Dockerfile").build();
        config.initAndValidate(logger);
        assertTrue(config.isDockerFileMode());
        assertEquals(config.getDockerFile(),new File("src/main/docker/Dockerfile"));
        assertEquals(config.getContextDir(),new File("src/main/docker"));
    }

    @Test
    // Tests fix for #1200
    public void simpleDockerfileWithoutParentDir() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                dockerFile("Dockerfile").build();
        config.initAndValidate(logger);
        assertTrue(config.isDockerFileMode());
        assertEquals(config.getDockerFile(),new File("Dockerfile"));
        assertEquals(config.getContextDir(), new File(""));
    }


    @Test
    public void simpleDockerfileDir() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                dockerFileDir("src/docker/").build();
        config.initAndValidate(logger);
        assertTrue(config.isDockerFileMode());
        assertEquals(config.getDockerFile(),new File("src/docker/Dockerfile"));
        assertFalse(config.getContextDir().isAbsolute());
    }

    @Test
    public void DockerfileDirAndDockerfileAlsoSet() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                dockerFileDir("/tmp/").
                dockerFile("Dockerfile").build();
        config.initAndValidate(logger);
        assertTrue(config.isDockerFileMode());
        assertEquals(config.getDockerFile(),new File("/tmp/Dockerfile"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void DockerfileDirAndDockerfileAlsoSetButDockerfileIsAbsoluteExceptionThrown() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                dockerFileDir("/tmp/").
                dockerFile(new File("Dockerfile").getAbsolutePath()).build();
        config.initAndValidate(logger);
    }

    @Test
    public void contextDir() {
        BuildImageConfiguration config =
                new BuildImageConfiguration.Builder().
                        contextDir("target").build();
        config.initAndValidate(logger);
        assertEquals(new File("target"), config.getContextDir());
    }

    @Test
    public void contextDirAndDockerfile() {
        BuildImageConfiguration config =
                new BuildImageConfiguration.Builder().
                        dockerFile("src/docker/Dockerfile").
                        contextDir("target").build();
        config.initAndValidate(logger);
        assertEquals(new File("target/src/docker/Dockerfile"), config.getDockerFile());
        assertEquals(new File("target"), config.getContextDir());
    }

    @Test
    public void contextDirAndDockerfileDir() {
        BuildImageConfiguration config =
                new BuildImageConfiguration.Builder().
                        dockerFileDir("src/docker").
                        contextDir("target").build();
        config.initAndValidate(logger);
        assertEquals(new File("target/Dockerfile"), config.getDockerFile());
        assertEquals(new File("target"), config.getContextDir());
    }

    @Test
    public void contextDirAndAbsoluteDockerfile() throws IOException {
        File tempDockerFile = File.createTempFile("Dockerfile", "");
        tempDockerFile.deleteOnExit();
        BuildImageConfiguration config = new BuildImageConfiguration.Builder()
                .dockerFile(tempDockerFile.getAbsolutePath())
                .contextDir("target")
                .build();

        // If contextDir is given and the dockerFile is an absolute path.
        // The Dockerfile should then be copied over.
        config.initAndValidate(logger);
        assertEquals(new File(tempDockerFile.getAbsolutePath()), config.getDockerFile());
        assertEquals(new File("target"), config.getContextDir());
    }

    @Test
    public void deprecatedDockerfileDir() {
        AssemblyConfiguration assemblyConfig = new AssemblyConfiguration.Builder().dockerFileDir("src/docker").build();
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                assembly(assemblyConfig).build();

        new Expectations() {{
            logger.warn(withSubstring("deprecated"));
        }};

        config.initAndValidate(logger);
        assertTrue(config.isDockerFileMode());
        assertEquals(config.getDockerFile(),new File("src/docker/Dockerfile"));
    }

    @Test
    public void dockerFileAndArchive() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                dockerArchive("this").
                dockerFile("that").build();

        try {
            config.initAndValidate(logger);
        } catch (IllegalArgumentException expected) {
            return;
        }
        fail("Should have failed.");
    }

    @Test
    public void dockerArchive() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                dockerArchive("this").build();
        config.initAndValidate(logger);

        assertFalse(config.isDockerFileMode());
        assertEquals(new File("this"), config.getDockerArchive());
    }

    @Test
    public void compression() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                compression("gzip").build();
        assertEquals(gzip, config.getCompression());

        config = new BuildImageConfiguration.Builder().build();
        assertEquals(none, config.getCompression());

        config =
            new BuildImageConfiguration.Builder().
                compression(null).build();
        assertEquals(none, config.getCompression());
    }


}
