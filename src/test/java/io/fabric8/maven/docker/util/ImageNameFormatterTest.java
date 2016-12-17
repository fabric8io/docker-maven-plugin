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

import java.util.Date;
import java.util.HashMap;
import java.util.Properties;

import mockit.*;
import mockit.integration.junit4.JMockit;
import org.apache.maven.project.MavenProject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.fail;

/**
 * @author roland
 * @since 07/06/16
 */
@RunWith(JMockit.class)
public class ImageNameFormatterTest {

    @Injectable
    private MavenProject project;

    @Injectable
    private Date now = new Date();

    @Tested
    private ImageNameFormatter formatter;

    @Test
    public void simple() throws Exception {
        assertThat(formatter.format("bla"),equalTo("bla"));
    }

    @Test
    public void invalidFormatChar() throws Exception {
        try {
            formatter.format("bla %z");
            fail();
        } catch (IllegalArgumentException exp) {
            System.out.println(exp);
            assertThat("Doesnt match", exp.getMessage(), containsString("%z"));
        }
    }

    @Test
    public void defaultUserName() throws Exception {

        new Expectations() {{
            project.getProperties(); result = new Properties();
        }};
        String[] data = {
            "io.fabric8", "fabric8",
            "io.FABRIC8", "fabric8",
            "io.fabric8.", "fabric8",
            "io.fabric8", "fabric8",
            "io.fabric8___", "fabric8__",
            "fabric8....", "fabric8"
        };
        for (int i = 0; i < data.length; i+=2) {
            new GroupIdExpectations(data[i]);
            String value = formatter.format("%g");
            assertThat("Idx. " + i / 2,value, equalTo(data[i+1]));
        }
    }

    @Test
    public void artifact() throws Exception {
        new Expectations() {{
            project.getArtifactId(); result = "Docker....Maven.....Plugin";
        }};

        assertThat(formatter.format("--> %a <--"),equalTo("--> docker.maven.plugin <--"));
    }

    @Test
    public void tagWithProperty() throws Exception {
        new PropertyLookupExpectations("docker.image.tag","1.2.3");
        assertThat(formatter.format("%t"),equalTo("1.2.3"));
        new FullVerifications() {{ }};
    }

    @Test
    public void tag() throws Exception {
        new Expectations() {{
            project.getArtifactId(); result = "docker-maven-plugin";
            project.getGroupId(); result = "io.fabric8";
            project.getVersion(); result = "1.2.3-SNAPSHOT";
            project.getProperties(); result = new Properties();
        }};
        assertThat(formatter.format("%g/%a:%l"), equalTo("fabric8/docker-maven-plugin:latest"));
        assertThat(formatter.format("%g/%a:%v"), equalTo("fabric8/docker-maven-plugin:1.2.3-SNAPSHOT"));
        assertThat(formatter.format("%g/%a:%t").matches(".*snapshot-[\\d-]+$"), is(true));
    }

    @Test
    public void nonSnapshotArtifact() throws Exception {
        new Expectations() {{
            project.getArtifactId(); result = "docker-maven-plugin";
            project.getGroupId(); result = "io.fabric8";
            project.getVersion(); result = "1.2.3";
            project.getProperties(); result = new Properties();
        }};

        assertThat(formatter.format("%g/%a:%l"), equalTo("fabric8/docker-maven-plugin:1.2.3"));
        assertThat(formatter.format("%g/%a:%v"), equalTo("fabric8/docker-maven-plugin:1.2.3"));
        assertThat(formatter.format("%g/%a:%t"), equalTo("fabric8/docker-maven-plugin:1.2.3"));
    }

    @Test
    public void groupIdWithProperty() throws Exception {
        new PropertyLookupExpectations("docker.image.user","this.it..is");
        assertThat(formatter.format("%g/name"),equalTo("this.it..is/name"));

        new FullVerifications() {{ }};
    }

    private final class GroupIdExpectations extends Expectations {
        GroupIdExpectations(String groupId) {
            project.getGroupId(); result = groupId;
        }

    }

    private final class PropertyLookupExpectations extends Expectations {

        public PropertyLookupExpectations(String key, String value) {
            Properties props = new Properties();
            props.put(key,value);
            project.getProperties(); result = props;
        }
    }
}
