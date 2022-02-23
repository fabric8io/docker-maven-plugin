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
import java.util.*;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.interpolation.fixed.FixedStringSearchInterpolator;
import org.codehaus.plexus.util.IOUtil;
import org.junit.Test;

import static io.fabric8.maven.docker.util.PathTestUtil.createTmpFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author roland
 * @since 21/01/16
 */
public class DockerFileUtilTest {

    @Test
    public void testSimple() throws Exception {
        File toTest = copyToTempDir("Dockerfile_from_simple");
        assertEquals("fabric8/s2i-java", DockerFileUtil.extractBaseImages(
            toTest, FixedStringSearchInterpolator.create(), Collections.emptyMap()).get(0));
    }

    @Test
    public void testMultiStage() throws Exception {
        File toTest = copyToTempDir("Dockerfile_multi_stage");
        Iterator<String> fromClauses = DockerFileUtil.extractBaseImages(
             toTest, FixedStringSearchInterpolator.create(), Collections.emptyMap()).iterator();

        assertEquals("fabric8/s2i-java", fromClauses.next());
        assertEquals("fabric8/s1i-java", fromClauses.next());
        assertFalse(fromClauses.hasNext());
    }

    @Test
    public void testMultiStageNamed() throws Exception {
        File toTest = copyToTempDir("Dockerfile_multi_stage_named_build_stages");
        Iterator<String> fromClauses = DockerFileUtil.extractBaseImages(
                toTest, FixedStringSearchInterpolator.create(), Collections.emptyMap()).iterator();

        assertEquals("fabric8/s2i-java", fromClauses.next());
        assertFalse(fromClauses.hasNext());
    }

    @Test
    public void testMultiStageWithArgs() throws Exception {
        File toTest = copyToTempDir("Dockerfile_multi_stage_with_args");
        Iterator<String> fromClauses = DockerFileUtil.extractBaseImages(
                toTest, FixedStringSearchInterpolator.create(), Collections.emptyMap()).iterator();

        assertEquals("fabric8/s2i-java:latest", fromClauses.next());
        assertEquals("busybox:latest", fromClauses.next());
        assertEquals("docker.io/library/openjdk:latest", fromClauses.next());
        assertFalse(fromClauses.hasNext());
    }

    @Test
    public void testExtractArgsFromDockerfile() {
        assertEquals("{VERSION=latest, FULL_IMAGE=busybox:latest}", DockerFileUtil.extractArgsFromLines(Arrays.asList(new String[]{"ARG", "VERSION:latest"}, new String[] {"ARG", "FULL_IMAGE=busybox:latest"}), Collections.emptyMap()).toString());
        assertEquals("{user1=someuser, buildno=1}", DockerFileUtil.extractArgsFromLines(Arrays.asList(new String[]{"ARG", "user1=someuser"}, new String[]{"ARG", "buildno=1"}), Collections.emptyMap()).toString());
        assertEquals("{NPM_VERSION=latest, NODE_VERSION=latest}", DockerFileUtil.extractArgsFromLines(Arrays.asList(new String[]{"ARG","NODE_VERSION=\"latest\""}, new String[]{"ARG",  "NPM_VERSION=\"latest\""}), Collections.emptyMap()).toString());
        assertEquals("{NPM_VERSION=latest, NODE_VERSION=latest}", DockerFileUtil.extractArgsFromLines(Arrays.asList(new String[]{"ARG","NODE_VERSION='latest'"}, new String[]{"ARG",  "NPM_VERSION='latest'"}), Collections.emptyMap()).toString());
        assertEquals("{MESSAGE=argument with spaces}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[] {"ARG", "MESSAGE='argument with spaces'"}), Collections.emptyMap()).toString());
        assertEquals("{MESSAGE=argument with spaces}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[] {"ARG", "MESSAGE=\"argument with spaces\""}), Collections.emptyMap()).toString());
        assertEquals("{TARGETPLATFORM=}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "TARGETPLATFORM"}), Collections.emptyMap()).toString());
        assertEquals("{TARGETPLATFORM=}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "TARGETPLATFORM="}), Collections.emptyMap()).toString());
        assertEquals("{TARGETPLATFORM=}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "TARGETPLATFORM:"}), Collections.emptyMap()).toString());
        assertEquals("{MESSAGE=argument:two}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "MESSAGE=argument:two"}), Collections.emptyMap()).toString());
        assertEquals("{MESSAGE2=argument=two}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "MESSAGE2=argument=two"}), Collections.emptyMap()).toString());
        assertEquals("{VER=0.0.3}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "VER=0.0.3"}), Collections.emptyMap()).toString());
        assertEquals("{VER={0.0.3}}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "VER={0.0.3}"}), Collections.emptyMap()).toString());
        assertEquals("{VER=[0.0.3]}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "VER=[0.0.3]"}), Collections.emptyMap()).toString());
        assertEquals("{VER={5,6}}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "VER={5,6}"}), Collections.emptyMap()).toString());
        assertEquals("{VER={5,6}}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "VER={5,6}"}), Collections.emptyMap()).toString());
        assertEquals("{VER={}}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "VER={}"}), Collections.emptyMap()).toString());
        assertEquals("{VER=====}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "VER====="}), Collections.emptyMap()).toString());
        assertEquals("{MESSAGE=:message}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "MESSAGE=:message"}), Collections.emptyMap()).toString());
        assertEquals("{MYAPP_IMAGE=myorg/myapp:latest}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "MYAPP_IMAGE=myorg/myapp:latest"}), Collections.emptyMap()).toString());
        assertEquals("{busyboxVersion=latest}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "busyboxVersion"}), Collections.singletonMap("busyboxVersion", "latest")).toString());
        assertEquals("{busyboxVersion=slim}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "busyboxVersion=latest"}), Collections.singletonMap("busyboxVersion", "slim")).toString());
        assertEquals("{busyboxVersion=latest}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "busyboxVersion=latest"}), null).toString());
    }

    @Test
    public void testExtractArgsFromDockerFile_parameterMapWithNullValues() {
        assertEquals("{VERSION=latest, FULL_IMAGE=busybox:latest}", DockerFileUtil.extractArgsFromLines(Arrays.asList(new String[]{"ARG", "VERSION:latest"}, new String[] {"ARG", "FULL_IMAGE=busybox:latest"}), Collections.singletonMap("VERSION", null)).toString());
        assertEquals("{VERSION=latest, FULL_IMAGE=busybox:latest}", DockerFileUtil.extractArgsFromLines(Arrays.asList(new String[]{"ARG", "VERSION=latest"}, new String[] {"ARG", "FULL_IMAGE=busybox:latest"}), Collections.singletonMap("VERSION", null)).toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidArgWithSpacesFromDockerfile() {
        DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "MY_IMAGE image with spaces"}), Collections.emptyMap());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidArgWithTrailingArgumentFromDockerfile() {
        DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "MESSAGE=foo bar"}), Collections.emptyMap());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidArgWithArrayWithSpaceFromDockerfile() {
        DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "MESSAGE=[5, 6]"}), Collections.emptyMap());
    }

    @Test
    public void testMultiStageNamedWithDuplicates() throws Exception {
        File toTest = copyToTempDir("Dockerfile_multi_stage_named_redundant_build_stages");
        Iterator<String> fromClauses = DockerFileUtil.extractBaseImages(
                toTest, FixedStringSearchInterpolator.create(), Collections.emptyMap()).iterator();

        assertEquals("centos", fromClauses.next());
        assertFalse(fromClauses.hasNext());

    }

    @Test
    public void testResolveArgValueFromStrContainingArgKey() {
        assertEquals("latest", DockerFileUtil.resolveImageTagFromArgs("$VERSION", Collections.singletonMap("VERSION", "latest")));
        assertEquals("test", DockerFileUtil.resolveImageTagFromArgs("${project.scope}", Collections.singletonMap("project.scope", "test")));
        assertEquals("test", DockerFileUtil.resolveImageTagFromArgs("$ad", Collections.singletonMap("ad", "test")));
        assertEquals("blatest", DockerFileUtil.resolveImageTagFromArgs("bla$ad", Collections.singletonMap("ad", "test")));
        assertEquals("testbar", DockerFileUtil.resolveImageTagFromArgs("${foo}bar", Collections.singletonMap("foo", "test")));
        assertEquals("bartest", DockerFileUtil.resolveImageTagFromArgs("bar${foo}", Collections.singletonMap("foo", "test")));
        assertEquals("$ad", DockerFileUtil.resolveImageTagFromArgs("$ad", Collections.emptyMap()));
    }

    @Test
    public void testFindAllArgsDefinedInString() {
        assertEquals(ImmutableSet.of("REPO_1", "IMAGE-1", "VERSION"), DockerFileUtil.findAllArgs("$REPO_1/bar${IMAGE-1}foo:$VERSION"));
        assertEquals(Collections.emptySet(), DockerFileUtil.findAllArgs("${invalidArg"));
    }

    @Test
    public void testDockerfileContainingFromPlatformFlag() throws IOException {
        File toTest = copyToTempDir("Dockerfile_from_contains_platform");
        assertEquals("fabric8/s2i-java:11", DockerFileUtil.extractBaseImages(
            toTest, FixedStringSearchInterpolator.create(), Collections.emptyMap()).get(0));
    }

    private File copyToTempDir(String resource) throws IOException {
        File dir = Files.createTempDirectory("d-m-p").toFile();
        File ret = new File(dir, "Dockerfile");
        try (FileOutputStream os = new FileOutputStream(ret)) {
            IOUtil.copy(getClass().getResourceAsStream(resource), os);
        }
        return ret;
    }

    @Test
    public void interpolate() throws Exception {
        MojoParameters params = mockMojoParams();
        Map<String, String> filterMapping = new HashMap<>();
        filterMapping.put("none", "false");
        filterMapping.put("var", "${*}");
        filterMapping.put("at", "@");

        for (Map.Entry<String, String> entry : filterMapping.entrySet()) {
            for (int i = 1; i < 2; i++) {
                File dockerFile = getDockerfilePath(i, entry.getKey());
                File expectedDockerFile = new File(dockerFile.getParent(), dockerFile.getName() + ".expected");
                File actualDockerFile = createTmpFile(dockerFile.getName());
                FixedStringSearchInterpolator interpolator = DockerFileUtil.createInterpolator(params, entry.getValue());
                FileUtils.write(actualDockerFile, DockerFileUtil.interpolate(dockerFile, interpolator), "UTF-8");
                // Compare text lines without regard to EOL delimiters
                assertEquals(FileUtils.readLines(expectedDockerFile), FileUtils.readLines(actualDockerFile));
            }
        }
    }

    private File getDockerfilePath(int i, String dir) {
        ClassLoader classLoader = getClass().getClassLoader();
        return new File(classLoader.getResource(
            String.format("interpolate/%s/Dockerfile_%d", dir, i)).getFile());
    }

    private MojoParameters mockMojoParams() {
        MavenProject project = new MavenProject();
        project.setArtifactId("docker-maven-plugin");

        Properties projectProperties = project.getProperties();
        projectProperties.put("base", "java");
        projectProperties.put("name", "guenther");
        projectProperties.put("age", "42");
        projectProperties.put("ext", "png");

        Settings settings = new Settings();
        ArtifactRepository localRepository = new MavenArtifactRepository() {
            public String getBasedir() {
                return "repository";
            }
        };
        @SuppressWarnings("deprecation")
        MavenSession session = new MavenSession(null, settings, localRepository, null, null, Collections.<String>emptyList(), ".", null, null, new Date(System.currentTimeMillis()));
        session.getUserProperties().setProperty("cliOverride", "cliValue"); // Maven CLI override: -DcliOverride=cliValue
        session.getSystemProperties().put("user.name", "somebody"); // Java system property: -Duser.name=somebody
        return new MojoParameters(session, project, null, null, null, settings, "src", "target", Collections.singletonList(project));
    }
}
