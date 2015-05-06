package org.jolokia.docker.maven.assembly;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.IOUtils;
import org.jolokia.docker.maven.config.Arguments;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

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
                .volumes(Collections.singletonList("/vol1")).content();

        String expected = loadFile("docker/Dockerfile.test");
        assertEquals(expected, stripCR(dockerfileContent));
    }

    @Test
    public void testMaintainer() {
        String dockerfileContent = new DockerFileBuilder().maintainer("maintainer@example.com").content();
        assertThat(dockerfileToMap(dockerfileContent), hasEntry("MAINTAINER", "maintainer@example.com"));
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

    @Test
    public void testExportBaseDir() {
        assertTrue(new DockerFileBuilder().basedir("/export").content().contains("/export"));
        assertFalse(new DockerFileBuilder().baseImage("java").basedir("/export").content().contains("/export"));
        assertTrue(new DockerFileBuilder().baseImage("java").exportBasedir(true).basedir("/export").content().contains("/export"));
        assertFalse(new DockerFileBuilder().baseImage("java").exportBasedir(false).basedir("/export").content().contains("/export"));
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
