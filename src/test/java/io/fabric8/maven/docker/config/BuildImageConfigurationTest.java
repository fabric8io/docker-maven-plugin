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

import io.fabric8.maven.docker.util.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static io.fabric8.maven.docker.config.ArchiveCompression.gzip;
import static io.fabric8.maven.docker.config.ArchiveCompression.none;

/**
 * @author roland
 * @since 04/04/16
 */

@ExtendWith(MockitoExtension.class)
class BuildImageConfigurationTest {

    @Mock
    Logger logger;

    @Test
    void empty() {
        BuildImageConfiguration config = new BuildImageConfiguration();
        config.initAndValidate(logger);
        Assertions.assertFalse(config.isDockerFileMode());
    }

    @Test
    void simpleDockerfile() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                dockerFile("src/main/docker/Dockerfile").build();
        config.initAndValidate(logger);
        Assertions.assertTrue(config.isDockerFileMode());
        Assertions.assertEquals(config.getDockerFile(), new File("src/main/docker/Dockerfile"));
        Assertions.assertEquals(config.getContextDir(), new File("src/main/docker"));
    }

    @Test
        // Tests fix for #1200
    void simpleDockerfileWithoutParentDir() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                dockerFile("Dockerfile").build();
        config.initAndValidate(logger);
        Assertions.assertTrue(config.isDockerFileMode());
        Assertions.assertEquals(config.getDockerFile(), new File("Dockerfile"));
        Assertions.assertEquals(config.getContextDir(), new File(""));
    }

    @Test
    void simpleDockerfileDir() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                dockerFileDir("src/docker/").build();
        config.initAndValidate(logger);
        Assertions.assertTrue(config.isDockerFileMode());
        Assertions.assertEquals(config.getDockerFile(), new File("src/docker/Dockerfile"));
        Assertions.assertFalse(config.getContextDir().isAbsolute());
    }

    @Test
    void DockerfileDirAndDockerfileAlsoSet() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                dockerFileDir("/tmp/").
                dockerFile("Dockerfile").build();
        config.initAndValidate(logger);
        Assertions.assertTrue(config.isDockerFileMode());
        Assertions.assertEquals(config.getDockerFile(), new File("/tmp/Dockerfile"));
    }

    @Test
    void DockerfileDirAndDockerfileAlsoSetButDockerfileIsAbsoluteExceptionThrown() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                dockerFileDir("/tmp/").
                dockerFile(new File("Dockerfile").getAbsolutePath()).build();
        Assertions.assertThrows(IllegalArgumentException.class, () -> config.initAndValidate(logger));
    }

    @Test
    void contextDir() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                contextDir("target").build();
        config.initAndValidate(logger);
        Assertions.assertEquals(new File("target"), config.getContextDir());
    }

    @Test
    void contextDirAndDockerfile() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                dockerFile("src/docker/Dockerfile").
                contextDir("target").build();
        config.initAndValidate(logger);
        Assertions.assertEquals(new File("target/src/docker/Dockerfile"), config.getDockerFile());
        Assertions.assertEquals(new File("target"), config.getContextDir());
    }

    @Test
    void contextDirAndDockerfileDir() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                dockerFileDir("src/docker").
                contextDir("target").build();
        config.initAndValidate(logger);
        Assertions.assertEquals(new File("target/Dockerfile"), config.getDockerFile());
        Assertions.assertEquals(new File("target"), config.getContextDir());
    }

    @Test
    void contextDirAndAbsoluteDockerfile() throws IOException {
        File tempDockerFile = File.createTempFile("Dockerfile", "");
        tempDockerFile.deleteOnExit();
        BuildImageConfiguration config = new BuildImageConfiguration.Builder()
            .dockerFile(tempDockerFile.getAbsolutePath())
            .contextDir("target")
            .build();

        // If contextDir is given and the dockerFile is an absolute path.
        // The Dockerfile should then be copied over.
        config.initAndValidate(logger);
        Assertions.assertEquals(new File(tempDockerFile.getAbsolutePath()), config.getDockerFile());
        Assertions.assertEquals(new File("target"), config.getContextDir());
    }

    @Test
    void deprecatedDockerfileDir() {
        AssemblyConfiguration assemblyConfig = new AssemblyConfiguration.Builder().dockerFileDir("src/docker").build();
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                assembly(assemblyConfig).build();

        config.initAndValidate(logger);
        Assertions.assertTrue(config.isDockerFileMode());
        Assertions.assertEquals(config.getDockerFile(), new File("src/docker/Dockerfile"));

        ArgumentCaptor<String> formatCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(logger, Mockito.times(2)).warn(formatCaptor.capture());
        Assertions.assertTrue(formatCaptor.getAllValues().get(0).contains("deprecated"));
    }

    @Test
    void dockerFileAndArchive() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                dockerArchive("this").
                dockerFile("that").build();

        Assertions.assertThrows(IllegalArgumentException.class, () -> config.initAndValidate(logger));
    }

    @Test
    void dockerArchive() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                dockerArchive("this").build();
        config.initAndValidate(logger);

        Assertions.assertFalse(config.isDockerFileMode());
        Assertions.assertEquals(new File("this"), config.getDockerArchive());
    }

    @Test
    void compression() {
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                compression("gzip").build();
        Assertions.assertEquals(gzip, config.getCompression());

        config = new BuildImageConfiguration.Builder().build();
        Assertions.assertEquals(none, config.getCompression());

        config =
            new BuildImageConfiguration.Builder().
                compression(null).build();
        Assertions.assertEquals(none, config.getCompression());
    }

    @Test
    void multipleAssembliesUniqueNames() {
        AssemblyConfiguration assemblyConfigurationOne = new AssemblyConfiguration.Builder().name("foo").build();
        AssemblyConfiguration assemblyConfigurationTwo = new AssemblyConfiguration.Builder().name("foo").build();
        BuildImageConfiguration config =
            new BuildImageConfiguration.Builder().
                assemblies(Arrays.asList(assemblyConfigurationOne, assemblyConfigurationTwo)).build();
        Assertions.assertThrows(IllegalArgumentException.class, () -> config.initAndValidate(logger));
    }
}
