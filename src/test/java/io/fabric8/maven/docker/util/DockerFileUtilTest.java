package io.fabric8.maven.docker.util;
/*
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
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.interpolation.fixed.FixedStringSearchInterpolator;
import org.codehaus.plexus.util.IOUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * @author roland
 * @since 21/01/16
 */
class DockerFileUtilTest {

    @Test
    void testSimple() throws Exception {
        File toTest = copyToTempDir("Dockerfile_from_simple");
        Assertions.assertEquals("fabric8/s2i-java", DockerFileUtil.extractBaseImages(
            toTest, FixedStringSearchInterpolator.create(), Collections.emptyMap()).get(0));
    }

    @Test
    void testMultiStage() throws Exception {
        File toTest = copyToTempDir("Dockerfile_multi_stage");
        Iterator<String> fromClauses = DockerFileUtil.extractBaseImages(
             toTest, FixedStringSearchInterpolator.create(), Collections.emptyMap()).iterator();

        Assertions.assertEquals("fabric8/s2i-java", fromClauses.next());
        Assertions.assertEquals("fabric8/s1i-java", fromClauses.next());
        Assertions.assertFalse(fromClauses.hasNext());
    }

    @Test
    void testMultiStageNamed() throws Exception {
        File toTest = copyToTempDir("Dockerfile_multi_stage_named_build_stages");
        Iterator<String> fromClauses = DockerFileUtil.extractBaseImages(
                toTest, FixedStringSearchInterpolator.create(), Collections.emptyMap()).iterator();

        Assertions.assertEquals("fabric8/s2i-java", fromClauses.next());
        Assertions.assertFalse(fromClauses.hasNext());
    }

    @Test
    void testMultiStageWithArgs() throws Exception {
        File toTest = copyToTempDir("Dockerfile_multi_stage_with_args");
        Iterator<String> fromClauses = DockerFileUtil.extractBaseImages(
                toTest, FixedStringSearchInterpolator.create(), Collections.emptyMap()).iterator();

        Assertions.assertEquals("fabric8/s2i-java:latest", fromClauses.next());
        Assertions.assertEquals("busybox:latest", fromClauses.next());
        Assertions.assertEquals("docker.io/library/openjdk:latest", fromClauses.next());
        Assertions.assertFalse(fromClauses.hasNext());
    }

    @Test
    void testExtractArgsFromDockerfile() {
        Assertions.assertEquals("{VERSION=latest, FULL_IMAGE=busybox:latest}", DockerFileUtil.extractArgsFromLines(Arrays.asList(new String[]{"ARG", "VERSION:latest"}, new String[] {"ARG", "FULL_IMAGE=busybox:latest"}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{user1=someuser, buildno=1}", DockerFileUtil.extractArgsFromLines(Arrays.asList(new String[]{"ARG", "user1=someuser"}, new String[]{"ARG", "buildno=1"}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{NPM_VERSION=latest, NODE_VERSION=latest}", DockerFileUtil.extractArgsFromLines(Arrays.asList(new String[]{"ARG","NODE_VERSION=\"latest\""}, new String[]{"ARG",  "NPM_VERSION=\"latest\""}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{NPM_VERSION=latest, NODE_VERSION=latest}", DockerFileUtil.extractArgsFromLines(Arrays.asList(new String[]{"ARG","NODE_VERSION='latest'"}, new String[]{"ARG",  "NPM_VERSION='latest'"}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{MESSAGE=argument with spaces}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[] {"ARG", "MESSAGE='argument with spaces'"}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{MESSAGE=argument with spaces}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[] {"ARG", "MESSAGE=\"argument with spaces\""}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{TARGETPLATFORM=}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "TARGETPLATFORM"}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{TARGETPLATFORM=}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "TARGETPLATFORM="}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{TARGETPLATFORM=}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "TARGETPLATFORM:"}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{MESSAGE=argument:two}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "MESSAGE=argument:two"}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{MESSAGE2=argument=two}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "MESSAGE2=argument=two"}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{VER=0.0.3}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "VER=0.0.3"}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{VER={0.0.3}}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "VER={0.0.3}"}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{VER=[0.0.3]}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "VER=[0.0.3]"}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{VER={5,6}}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "VER={5,6}"}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{VER={5,6}}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "VER={5,6}"}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{VER={}}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "VER={}"}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{VER=====}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "VER====="}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{MESSAGE=:message}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "MESSAGE=:message"}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{MYAPP_IMAGE=myorg/myapp:latest}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "MYAPP_IMAGE=myorg/myapp:latest"}), Collections.emptyMap()).toString());
        Assertions.assertEquals("{busyboxVersion=latest}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "busyboxVersion"}), Collections.singletonMap("busyboxVersion", "latest")).toString());
        Assertions.assertEquals("{busyboxVersion=slim}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "busyboxVersion=latest"}), Collections.singletonMap("busyboxVersion", "slim")).toString());
        Assertions.assertEquals("{busyboxVersion=latest}", DockerFileUtil.extractArgsFromLines(Collections.singletonList(new String[]{"ARG", "busyboxVersion=latest"}), null).toString());
    }

    @Test
    void testExtractArgsFromDockerFile_parameterMapWithNullValues() {
        Assertions.assertEquals("{VERSION=latest, FULL_IMAGE=busybox:latest}", DockerFileUtil.extractArgsFromLines(Arrays.asList(new String[]{"ARG", "VERSION:latest"}, new String[] {"ARG", "FULL_IMAGE=busybox:latest"}), Collections.singletonMap("VERSION", null)).toString());
        Assertions.assertEquals("{VERSION=latest, FULL_IMAGE=busybox:latest}", DockerFileUtil.extractArgsFromLines(Arrays.asList(new String[]{"ARG", "VERSION=latest"}, new String[] {"ARG", "FULL_IMAGE=busybox:latest"}), Collections.singletonMap("VERSION", null)).toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MY_IMAGE image with spaces" , "MESSAGE=foo bar" , "MESSAGE=[5, 6]"} )
    void testInvalidArgWithSpacesFromDockerfile(String arg) {
        List<String[]> argLines = Collections.singletonList(new String[] { "ARG", arg});
        Map<String, String> argsFromBuildConfig = Collections.emptyMap();
        Assertions.assertThrows(IllegalArgumentException.class, () ->DockerFileUtil.extractArgsFromLines(argLines, argsFromBuildConfig));
    }

    @Test
    void testMultiStageNamedWithDuplicates() throws Exception {
        File toTest = copyToTempDir("Dockerfile_multi_stage_named_redundant_build_stages");
        Iterator<String> fromClauses = DockerFileUtil.extractBaseImages(
                toTest, FixedStringSearchInterpolator.create(), Collections.emptyMap()).iterator();

        Assertions.assertEquals("centos", fromClauses.next());
        Assertions.assertFalse(fromClauses.hasNext());

    }

    @Test
    void testResolveArgValueFromStrContainingArgKey() {
        Assertions.assertEquals("latest", DockerFileUtil.resolveImageTagFromArgs("$VERSION", Collections.singletonMap("VERSION", "latest")));
        Assertions.assertEquals("test", DockerFileUtil.resolveImageTagFromArgs("${project.scope}", Collections.singletonMap("project.scope", "test")));
        Assertions.assertEquals("test", DockerFileUtil.resolveImageTagFromArgs("$ad", Collections.singletonMap("ad", "test")));
        Assertions.assertEquals("blatest", DockerFileUtil.resolveImageTagFromArgs("bla$ad", Collections.singletonMap("ad", "test")));
        Assertions.assertEquals("testbar", DockerFileUtil.resolveImageTagFromArgs("${foo}bar", Collections.singletonMap("foo", "test")));
        Assertions.assertEquals("bartest", DockerFileUtil.resolveImageTagFromArgs("bar${foo}", Collections.singletonMap("foo", "test")));
        Assertions.assertEquals("$ad", DockerFileUtil.resolveImageTagFromArgs("$ad", Collections.emptyMap()));
    }

    @Test
    void testFindAllArgsDefinedInString() {
        Assertions.assertEquals(ImmutableSet.of("REPO_1", "IMAGE-1", "VERSION"), DockerFileUtil.findAllArgs("$REPO_1/bar${IMAGE-1}foo:$VERSION"));
        Assertions.assertEquals(Collections.emptySet(), DockerFileUtil.findAllArgs("${invalidArg"));
    }

    @Test
    void testDockerfileContainingFromPlatformFlag() throws IOException {
        File toTest = copyToTempDir("Dockerfile_from_contains_platform");
        Assertions.assertEquals("fabric8/s2i-java:11", DockerFileUtil.extractBaseImages(
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

    @ParameterizedTest
    @CsvSource({
        "none, false",
        "var, ${*}",
        "at, @"
    })
    void interpolate(String key, String value, @TempDir Path tempDir) throws Exception {
        MojoParameters params = mockMojoParams();
        File dockerFile = getDockerfilePath(key);
        File expectedDockerFile = new File(dockerFile.getParent(), dockerFile.getName() + ".expected");
        File actualDockerFile = Files.createFile(tempDir.resolve(dockerFile.getName())).toFile();
        FixedStringSearchInterpolator interpolator = DockerFileUtil.createInterpolator(params, value);
        FileUtils.write(actualDockerFile, DockerFileUtil.interpolate(dockerFile, interpolator), "UTF-8");
        // Compare text lines without regard to EOL delimiters
        Assertions.assertEquals(readFile(expectedDockerFile), readFile(actualDockerFile));
    }

    private static List<String> readFile(File file) throws IOException {
        try (Stream<String> lines = Files.lines(file.toPath())) {
            return lines.collect(Collectors.toList());
        }
    }

    private File getDockerfilePath(String dir) {
        ClassLoader classLoader = getClass().getClassLoader();
        return new File(classLoader.getResource(
            String.format("interpolate/%s/Dockerfile_1", dir)).getFile());
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
        MavenSession session = new MavenSession(null, settings, localRepository, null, null, Collections.emptyList(), ".", null, null, new Date(System.currentTimeMillis()));
        session.getUserProperties().setProperty("cliOverride", "cliValue"); // Maven CLI override: -DcliOverride=cliValue
        session.getSystemProperties().put("user.name", "somebody"); // Java system property: -Duser.name=somebody
        return new MojoParameters(session, project, null, null, null, settings, "src", "target", Collections.singletonList(project));
    }
}
