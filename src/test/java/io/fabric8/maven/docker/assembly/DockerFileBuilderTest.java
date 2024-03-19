package io.fabric8.maven.docker.assembly;

import com.google.common.collect.ImmutableMap;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.HealthCheckConfiguration;
import io.fabric8.maven.docker.config.HealthCheckMode;
import io.fabric8.maven.docker.config.RunCommand;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

 class DockerFileBuilderTest {

    @Test
     void testBuildDockerFile() throws Exception {
        Arguments a = Arguments.Builder.get().withParam("c1").withParam("c2").build();
        Arguments b = Arguments.Builder.get().withParam("/bin/sh").withParam("-c").build();
        String dockerfileContent = new DockerFileBuilder().add("/src", "/dest")
                .baseImage("image")
                .cmd(a)
                .env(ImmutableMap.of("foo", "bar"))
                .basedir("/export")
                .expose(Collections.singletonList("8080"))
                .maintainer("maintainer@example.com")
                .workdir("/tmp")
                .labels(ImmutableMap.of("com.acme.foobar", "How are \"you\" ?"))
                .volumes(Collections.singletonList("/vol1"))
                .shell(b)
                .run(RunCommand.run("echo something", "echo second"))
                .content();
        String expected = loadFile("docker/Dockerfile.test");
        Assertions.assertEquals(expected, stripCR(dockerfileContent));
    }

    @Test
    void testBuildDockerFileUserChange() throws IOException {
        Arguments a = Arguments.Builder.get().withParam("c1").withParam("c2").build();
        Arguments b = Arguments.Builder.get().withParam("/bin/sh").withParam("-c").build();
        String dockerfileContent = new DockerFileBuilder().add("/src", "/dest")
                .baseImage("image")
                .cmd(a)
                .env(ImmutableMap.of("foo", "bar"))
                .basedir("/export")
                .expose(Collections.singletonList("8080"))
                .maintainer("maintainer@example.com")
                .workdir("/tmp")
                .labels(ImmutableMap.of("com.acme.foobar", "How are \"you\" ?"))
                .volumes(Collections.singletonList("/vol1"))
                .shell(b)
                .run(Arrays.asList(
                        new RunCommand(DockerFileKeyword.USER, "root"),
                        new RunCommand("echo something"),
                        new RunCommand(DockerFileKeyword.USER, "notroot"),
                        new RunCommand("echo second")))
                .content();
        String expected = loadFile("docker/Dockerfile_user_change.test");
        Assertions.assertEquals(expected, stripCR(dockerfileContent));
    }

    @Test
     void testBuildDockerShellArgumentsForShell() throws Exception {
        Arguments a = Arguments.Builder.get().withParam("c1").withParam("c2").build();
        Arguments b = new Arguments("/bin/sh -c");
        String dockerfileContent = new DockerFileBuilder().add("/src", "/dest")
                .baseImage("image")
                .cmd(a)
                .env(ImmutableMap.of("foo", "bar"))
                .basedir("/export")
                .expose(Collections.singletonList("8080"))
                .maintainer("maintainer@example.com")
                .workdir("/tmp")
                .labels(ImmutableMap.of("com.acme.foobar", "How are \"you\" ?"))
                .volumes(Collections.singletonList("/vol1"))
                .shell(b)
                .run(RunCommand.run("echo something", "echo second"))
                .content();
        String expected = loadFile("docker/Dockerfile.test");
        Assertions.assertEquals(expected, stripCR(dockerfileContent));
    }

    @Test
     void testBuildDockerFileMultilineLabel() throws Exception {
        Arguments a = Arguments.Builder.get().withParam("c1").withParam("c2").build();
        String dockerfileContent = new DockerFileBuilder()
                .add("/src", "/dest")
                .baseImage("image")
                .cmd(a)
                .labels(ImmutableMap.of("key", "unquoted",
                        "flag", "",
                        "with_space", "1.fc nuremberg",
                        "some-json", "{\n  \"key\": \"value\"\n}\n"))
                .content();
        String expected = loadFile("docker/Dockerfile.multiline_label.test");
        Assertions.assertEquals(expected, stripCR(dockerfileContent));
    }

    @Test
     void testBuildLabelWithSpace()  {
        String dockerfileContent = new DockerFileBuilder()
                .labels(ImmutableMap.of("key", "label with space"))
                .content();
        Assertions.assertTrue(stripCR(dockerfileContent).contains("LABEL key=\"label with space\""));
    }

    @Test
     void testBuildDockerFileUDPPort() throws IOException {
        Arguments a = Arguments.Builder.get().withParam("c1").withParam("c2").build();
        String dockerfileContent = new DockerFileBuilder().add("/src", "/dest")
                .baseImage("image")
                .cmd(a)
                .basedir("/export")
                .expose(Collections.singletonList("8080/udp"))
                .maintainer("maintainer@example.com")
                .workdir("/tmp")
                .volumes(Collections.singletonList("/vol1"))
                .run(RunCommand.run("echo something", "echo second"))
                .content();
        String expected = loadFile("docker/Dockerfile_udp.test");
        Assertions.assertEquals(expected, stripCR(dockerfileContent));
    }

    @Test
    void testBuildDockerFileExplicitTCPPort() throws IOException {
        Arguments a = Arguments.Builder.get().withParam("c1").withParam("c2").build();
        String dockerfileContent = new DockerFileBuilder().add("/src", "/dest")
                .baseImage("image")
                .cmd(a)
                .basedir("/export")
                .expose(Collections.singletonList("8080/tcp"))
                .maintainer("maintainer@example.com")
                .workdir("/tmp")
                .volumes(Collections.singletonList("/vol1"))
                .run(RunCommand.run("echo something", "echo second"))
                .content();
        String expected = loadFile("docker/Dockerfile_tcp.test");
        Assertions.assertEquals(expected, stripCR(dockerfileContent));
    }

    private static DockerFileBuilder getFileBuilder() {
        Arguments a = Arguments.Builder.get().withParam("c1").withParam("c2").build();
        DockerFileBuilder builder = new DockerFileBuilder().add("/src", "/dest")
            .baseImage("image")
            .cmd(a)
            .env(ImmutableMap.of("foo", "bar"))
            .basedir("/export")
            .maintainer("maintainer@example.com")
            .workdir("/tmp")
            .labels(ImmutableMap.of("com.acme.foobar", "How are \"you\" ?"))
            .volumes(Collections.singletonList("/vol1"))
            .run(RunCommand.run("echo something", "echo second"));
        return builder;
    }

    @Test
     void testBuildDockerFileBadPort() {
        DockerFileBuilder builder = getFileBuilder().expose(Collections.singletonList("8080aaa/udp"));
        Assertions.assertThrows(IllegalArgumentException.class, builder::content);
    }

    @Test
     void testBuildDockerFileBadProtocol() {
        DockerFileBuilder builder = getFileBuilder().expose(Collections.singletonList("8080/bogusdatagram"));
        Assertions.assertThrows(IllegalArgumentException.class, builder::content);
    }

    @Test
    void testDockerFileOptimisation() throws Exception {
        String dockerfileContent = getFileBuilder()
            .expose(Collections.singletonList("8080"))
            .run(RunCommand.run("echo third", "echo fourth", "echo fifth"))
            .optimise()
            .content();
        String expected = loadFile("docker/Dockerfile_optimised.test");
        Assertions.assertEquals(expected, stripCR(dockerfileContent));
    }

    @Test
    void testMaintainer() {
        String dockerfileContent = new DockerFileBuilder().maintainer("maintainer@example.com").content();
        Assertions.assertEquals("maintainer@example.com", dockerfileToMap(dockerfileContent).get("MAINTAINER"));
    }

    @Test
    void testOptimise() {
        String dockerfileContent = new DockerFileBuilder().optimise().run(RunCommand.run("echo something", "echo two")).content();
        Assertions.assertEquals("echo something && echo two", dockerfileToMap(dockerfileContent).get("RUN"));
    }

    @Test
    void testOptimiseOnEmptyRunCommandListDoesNotThrowException() {
        DockerFileBuilder builder = new DockerFileBuilder().optimise();
        Assertions.assertDoesNotThrow(builder::content);
    }

    @Test
    void testEntryPointShell() {
        Arguments a = Arguments.Builder.get().withShell("java -jar /my-app-1.1.1.jar server").build();
        String dockerfileContent = new DockerFileBuilder().entryPoint(a).content();
        Assertions.assertEquals("java -jar /my-app-1.1.1.jar server", dockerfileToMap(dockerfileContent).get("ENTRYPOINT"));
    }

    @Test
    void testEntryPointParams() {
        Arguments a = Arguments.Builder.get().withParam("java").withParam("-jar").withParam("/my-app-1.1.1.jar").withParam("server").build();
        String dockerfileContent = new DockerFileBuilder().entryPoint(a).content();
        Assertions.assertEquals("[\"java\",\"-jar\",\"/my-app-1.1.1.jar\",\"server\"]", dockerfileToMap(dockerfileContent).get("ENTRYPOINT"));
    }

    @Test
    void testHealthCheckCmdParams() {
        HealthCheckConfiguration hc = new HealthCheckConfiguration.Builder().cmd(new Arguments("echo hello")).interval("5s").timeout("3s").startPeriod("30s").retries(4).build();
        String dockerfileContent = new DockerFileBuilder().healthCheck(hc).content();
        Assertions.assertEquals("--interval=5s --timeout=3s --start-period=30s --retries=4 CMD echo hello", dockerfileToMap(dockerfileContent).get("HEALTHCHECK"));
    }

    @Test
    void testHealthCheckNone() {
        HealthCheckConfiguration hc = new HealthCheckConfiguration.Builder().mode(HealthCheckMode.none).build();
        String dockerfileContent = new DockerFileBuilder().healthCheck(hc).content();
        Assertions.assertEquals("NONE", dockerfileToMap(dockerfileContent).get("HEALTHCHECK"));
    }

    @Test
    void testNoRootExport() {
        Assertions.assertFalse(new DockerFileBuilder().add("/src", "/dest").basedir("/").content().contains("VOLUME"));
    }

    @Test
    void illegalNonAbsoluteBaseDir() {
        DockerFileBuilder builder = new DockerFileBuilder();
        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.basedir("blub"));
    }

    @Test
    void testAssemblyUserWithChown() {
        String dockerfileContent = new DockerFileBuilder().assemblyUser("jboss:jboss:jboss")
                                                   .add("a","a/nested").add("b","b/deeper/nested").content();
        Assertions.assertEquals("--chown=jboss:jboss b /maven/b/deeper/nested", dockerfileToMap(dockerfileContent).get("COPY"));
    }

    @Test
    void testUser() {
        String dockerFile = new DockerFileBuilder().assemblyUser("jboss:jboss:jboss").user("bob")
                                                   .add("a","a/nested").add("b","b/deeper/nested").content();
        String EXPECTED_REGEXP = "USER bob$";
        Pattern pattern = Pattern.compile(EXPECTED_REGEXP);
        Assertions.assertTrue(pattern.matcher(dockerFile).find());
    }

    @Test
    void testAssemblyWithTargetSettings() {
        String dockerFile = new DockerFileBuilder().assemblyUser("test:test:test")
                .add("a","a/nested", null, null, true)
                .add("b","b/deeper/nested", null, "jboss:jboss:jboss", false)
                .content();
        List<String> dockerfile = dockerfileToList(dockerFile);
        Assertions.assertTrue(dockerfile.contains("COPY --chown=test:test a /maven/a/nested"));
        Assertions.assertTrue(dockerfile.contains("COPY --chown=jboss:jboss b /maven/b/deeper/nested"));
        Assertions.assertTrue(dockerfile.contains("VOLUME [\"/maven\"]"));
    }


    @Test
    void testExportBaseDir() {
        Assertions.assertTrue(new DockerFileBuilder().basedir("/export").content().contains("/export"));
        Assertions.assertFalse(new DockerFileBuilder().baseImage("java").basedir("/export").content().contains("/export"));
        Assertions.assertTrue(new DockerFileBuilder().baseImage("java").exportTargetDir(true).basedir("/export").content().contains("/export"));
        Assertions.assertFalse(new DockerFileBuilder().baseImage("java").exportTargetDir(false).basedir("/export").content().contains("/export"));
    }

    @Test
    void testTargetDirStartsWithEnvVar() {
        Assertions.assertTrue(new DockerFileBuilder().basedir("${FOO}").content().contains("${FOO}"));
        Assertions.assertTrue(new DockerFileBuilder().basedir("$FOO").content().contains("$FOO"));
        Assertions.assertTrue(new DockerFileBuilder().basedir("${FOO}/").content().contains("${FOO}"));
        Assertions.assertTrue(new DockerFileBuilder().basedir("$FOO/").content().contains("$FOO"));
        Assertions.assertTrue(new DockerFileBuilder().basedir("${FOO}/bar").content().contains("${FOO}/bar"));
        Assertions.assertTrue(new DockerFileBuilder().basedir("$FOO/bar").content().contains("$FOO/bar"));
    }

    @Test
    void testDockerFileKeywords() {
        StringBuilder b = new StringBuilder();
        DockerFileKeyword.RUN.addTo(b, "apt-get", "update");
        Assertions.assertEquals("RUN apt-get update\n", b.toString());

        b = new StringBuilder();
        DockerFileKeyword.EXPOSE.addTo(b, new String[]{"1010", "2020"});
        Assertions.assertEquals("EXPOSE 1010 2020\n",b.toString());

        b = new StringBuilder();
        DockerFileKeyword.USER.addTo(b, "roland");
        Assertions.assertEquals("USER roland\n",b.toString());
    }

    private String stripCR(String input){
    	return input.replaceAll("\r", "");
    }

    private String loadFile(String fileName) throws IOException {
        return stripCR(IOUtils.toString(getClass().getClassLoader().getResource(fileName)));
    }

    private static Map<String, String> dockerfileToMap(String dockerFile) {
        final Map<String, String> dockerfileMap = new HashMap<>();
        final Scanner scanner = new Scanner(dockerFile);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.trim().length() == 0) {
                continue;
            }
            String[] commandAndArguments = line.trim().split("\\s+", 2);
            if (commandAndArguments.length < 2) {
                continue;
            }
            dockerfileMap.put(commandAndArguments[0], commandAndArguments[1]);
        }
        scanner.close();
        return dockerfileMap;
    }

    private static List<String> dockerfileToList(String dockerFile) {
        final List<String> dockerfileMap = new ArrayList<>();
        final Scanner scanner = new Scanner(dockerFile);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.trim().length() == 0) {
                continue;
            }
            String[] commandAndArguments = line.trim().split("\\s+", 2);
            if (commandAndArguments.length < 2) {
                continue;
            }
            dockerfileMap.add(line.trim());
        }
        scanner.close();
        return dockerfileMap;
    }
}
