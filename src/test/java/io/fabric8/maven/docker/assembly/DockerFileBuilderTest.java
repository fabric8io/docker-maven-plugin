package io.fabric8.maven.docker.assembly;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableMap;
import io.fabric8.maven.docker.config.Arguments;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import static org.hamcrest.Matchers.hasEntry;
import static org.junit.Assert.*;


public class DockerFileBuilderTest {

    @Test
    public void testBuildDockerFile() throws Exception {
        Arguments a = Arguments.Builder.get().withParam("c1").withParam("c2").build();
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
                .run(Arrays.asList("echo something", "echo second"))
                .content();
        String expected = loadFile("docker/Dockerfile.test");
        assertEquals(expected, stripCR(dockerfileContent));
    }

    @Test
    public void testDockerFileOptimisation() throws Exception {
        Arguments a = Arguments.Builder.get().withParam("c1").withParam("c2").build();
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
                .run(Arrays.asList("echo something", "echo second", "echo third", "echo fourth", "echo fifth"))
                .optimise()
                .content();
        String expected = loadFile("docker/Dockerfile_optimised.test");
        assertEquals(expected, stripCR(dockerfileContent));
    }

    @Test
    public void testMaintainer() {
        String dockerfileContent = new DockerFileBuilder().maintainer("maintainer@example.com").content();
        assertThat(dockerfileToMap(dockerfileContent), hasEntry("MAINTAINER", "maintainer@example.com"));
    }

    @Test
    public void testOptimise() {
        String dockerfileContent = new DockerFileBuilder().optimise().run(Arrays.asList("echo something", "echo two")).content();
        assertThat(dockerfileToMap(dockerfileContent), hasEntry("RUN", "echo something && echo two"));
    }

    @Test
    public void testOptimiseOnEmptyRunCommandListDoesNotThrowException() {
        new DockerFileBuilder().optimise().content();
    }

    @Test
    public void testEntryPointShell() {
        Arguments a = Arguments.Builder.get().withShell("java -jar /my-app-1.1.1.jar server").build();
        String dockerfileContent = new DockerFileBuilder().entryPoint(a).content();
        assertThat(dockerfileToMap(dockerfileContent), hasEntry("ENTRYPOINT", "java -jar /my-app-1.1.1.jar server"));
    }

    @Test
    public void testEntryPointParams() {
        Arguments a = Arguments.Builder.get().withParam("java").withParam("-jar").withParam("/my-app-1.1.1.jar").withParam("server").build();
        String dockerfileContent = new DockerFileBuilder().entryPoint(a).content();
        assertThat(dockerfileToMap(dockerfileContent), hasEntry("ENTRYPOINT", "[\"java\",\"-jar\",\"/my-app-1.1.1.jar\",\"server\"]"));
    }

    @Test
    public void testNoRootExport() {
        assertFalse(new DockerFileBuilder().add("/src", "/dest").basedir("/").content().contains("VOLUME"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalNonAbsoluteBaseDir() {
        new DockerFileBuilder().basedir("blub").content();
    }

    @Test
    public void testAssemblyUserWithChown() {
        String dockerFile = new DockerFileBuilder().assemblyUser("jboss:jboss:jboss")
                                                   .add("a","a/nested").add("b","b/deeper/nested").content();
        String EXPECTED_REGEXP = "chown\\s+-R\\s+jboss:jboss\\s+([^\\s]+)"
                                 + "\\s+&&\\s+cp\\s+-rp\\s+\\1/\\*\\s+/\\s+&&\\s+rm\\s+-rf\\s+\\1";
        Pattern pattern = Pattern.compile(EXPECTED_REGEXP);
        assertTrue(pattern.matcher(dockerFile).find());
    }

    @Test
    public void testUser() {
        String dockerFile = new DockerFileBuilder().assemblyUser("jboss:jboss:jboss").user("bob")
                                                   .add("a","a/nested").add("b","b/deeper/nested").content();
        String EXPECTED_REGEXP = "USER bob$";
        Pattern pattern = Pattern.compile(EXPECTED_REGEXP);
        assertTrue(pattern.matcher(dockerFile).find());
    }


    @Test
    public void testExportBaseDir() {
        assertTrue(new DockerFileBuilder().basedir("/export").content().contains("/export"));
        assertFalse(new DockerFileBuilder().baseImage("java").basedir("/export").content().contains("/export"));
        assertTrue(new DockerFileBuilder().baseImage("java").exportBasedir(true).basedir("/export").content().contains("/export"));
        assertFalse(new DockerFileBuilder().baseImage("java").exportBasedir(false).basedir("/export").content().contains("/export"));
    }

    @Test
    public void testDockerFileKeywords() {
        StringBuilder b = new StringBuilder();
        DockerFileKeyword.RUN.addTo(b, "apt-get", "update");
        assertEquals("RUN apt-get update\n", b.toString());

        b = new StringBuilder();
        DockerFileKeyword.EXPOSE.addTo(b, new String[]{"1010", "2020"});
        assertEquals("EXPOSE 1010 2020\n",b.toString());

        b = new StringBuilder();
        DockerFileKeyword.USER.addTo(b, "roland");
        assertEquals("USER roland\n",b.toString());
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
}
