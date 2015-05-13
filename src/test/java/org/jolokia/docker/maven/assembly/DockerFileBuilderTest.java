package org.jolokia.docker.maven.assembly;

import java.io.IOException;
import java.util.*;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.hasEntry;
import static org.junit.Assert.*;


public class DockerFileBuilderTest {

    private String dockerfileContent;
    private Map<String, String> dockerfileMap;

    @Before
    public void setUp() throws Exception {
        Map<String, String> env = new HashMap<>(1);
        env.put("foo", "bar");

        dockerfileContent = new DockerFileBuilder().add("/src", "/dest")
                .baseImage("image")
                .command("c1", "c2")
                .env(env)
                .basedir("/export")
                .expose(Arrays.asList("8080"))
                .maintainer("maintainer@example.com")
                .volumes(Arrays.asList("/vol1")).content();
        dockerfileMap = dockerfileToMap(dockerfileContent);
    }

    @Test
    public void testBuildDockerFile() throws Exception {
        String expected = loadFile("docker/Dockerfile.test");
        assertEquals(expected, stripCR(dockerfileContent));
    }

    @Test
    public void testMaintainer() {
        assertThat(dockerfileMap, hasEntry("MAINTAINER", "maintainer@example.com"));
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
