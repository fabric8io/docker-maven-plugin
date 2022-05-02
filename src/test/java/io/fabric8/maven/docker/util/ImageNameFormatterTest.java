package io.fabric8.maven.docker.util;
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

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.Properties;

/**
 * @author roland
 * @since 07/06/16
 */

@ExtendWith(MockitoExtension.class)
class ImageNameFormatterTest {

    @Mock
    private MavenProject project;

    @Mock
    private Date now;

    @InjectMocks
    private ImageNameFormatter formatter;

    @Test
    void simple() {
        Assertions.assertEquals("bla", formatter.format("bla"));
    }

    @Test
    void invalidFormatChar() {
        IllegalArgumentException exp = Assertions.assertThrows(IllegalArgumentException.class, () -> formatter.format("bla %z"));
        Assertions.assertTrue(exp.getMessage().contains("%z"));
    }

    @ParameterizedTest
    @CsvSource({
        "io.fabric8, fabric8",
        "io.FABRIC8, fabric8",
        "io.fabric8., fabric8",
        "io.fabric8, fabric8",
        "fabric8...., fabric8",
        "io.fabric8___, fabric8__"
    })
    void defaultUserName(String groupId, String expected) {
        Mockito.doReturn(new Properties()).when(project).getProperties();
        Mockito.doReturn(groupId).when(project).getGroupId();

        String value = formatter.format("%g");
        Assertions.assertEquals(expected, value);
    }

    @Test
    void artifact() {
        Mockito.doReturn("Docker....Maven.....Plugin").when(project).getArtifactId();
        Assertions.assertEquals("--> docker.maven.plugin <--", formatter.format("--> %a <--"));
    }

    @Test
    void tagWithProperty() {
        Properties props = new Properties();
        props.put("docker.image.tag", "1.2.3");
        Mockito.doReturn(props).when(project).getProperties();
        Assertions.assertEquals("1.2.3", formatter.format("%t"));
    }

    @Test
    void tag() {
        Mockito.doReturn("docker-maven-plugin").when(project).getArtifactId();
        Mockito.doReturn("io.fabric8").when(project).getGroupId();
        Mockito.doReturn("1.2.3-SNAPSHOT").when(project).getVersion();
        Mockito.doReturn(new Properties()).when(project).getProperties();

        Assertions.assertEquals("fabric8/docker-maven-plugin:latest", formatter.format("%g/%a:%l"));
        Assertions.assertEquals("fabric8/docker-maven-plugin:1.2.3-SNAPSHOT", formatter.format("%g/%a:%v"));
        Assertions.assertTrue(formatter.format("%g/%a:%t").matches(".*snapshot-[\\d-]+$"));
    }

    @Test
    void timestamp() {
        Mockito.doReturn("docker-maven-plugin").when(project).getArtifactId();
        Mockito.doReturn("io.fabric8").when(project).getGroupId();
        Mockito.doReturn(new Properties()).when(project).getProperties();

        Assertions.assertTrue(formatter.format("%g/%a:%T").matches("^fabric8/docker-maven-plugin:[\\d-]+$"));
        Assertions.assertTrue(formatter.format("%g/%a:test-%T").matches("^fabric8/docker-maven-plugin:test-[\\d-]+$"));
    }

    @Test
    void tagWithDockerImageTagSet() {
        Mockito.doReturn("docker-maven-plugin").when(project).getArtifactId();
        Mockito.doReturn("io.fabric8").when(project).getGroupId();
        Mockito.doReturn("1.2.3-SNAPSHOT").when(project).getVersion();
        Mockito.doReturn(new Properties()).when(project).getProperties();

        project.getProperties().setProperty("docker.image.tag", "%l");

        Assertions.assertEquals("fabric8/docker-maven-plugin:latest", formatter.format("%g/%a:%l"));
    }

    @Test
    void nonSnapshotArtifact() {
        Mockito.doReturn("docker-maven-plugin").when(project).getArtifactId();
        Mockito.doReturn("io.fabric8").when(project).getGroupId();
        Mockito.doReturn("1.2.3").when(project).getVersion();
        Mockito.doReturn(new Properties()).when(project).getProperties();

        Assertions.assertEquals("fabric8/docker-maven-plugin:1.2.3", formatter.format("%g/%a:%l"));
        Assertions.assertEquals("fabric8/docker-maven-plugin:1.2.3", formatter.format("%g/%a:%v"));
        Assertions.assertEquals("fabric8/docker-maven-plugin:1.2.3", formatter.format("%g/%a:%t"));
    }

    @Test
    void groupIdWithProperty() {
        Properties props = new Properties();
        props.put("docker.image.user", "this.it..is");
        Mockito.doReturn(props).when(project).getProperties();

        Assertions.assertEquals("this.it..is/name", formatter.format("%g/name"));
    }
}
