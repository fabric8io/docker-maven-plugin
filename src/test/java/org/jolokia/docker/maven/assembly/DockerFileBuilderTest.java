package org.jolokia.docker.maven.assembly;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.junit.Test;


public class DockerFileBuilderTest {

    @Test
    public void testBuildDockerFile() throws Exception {
        Map<String, String> env = new HashMap<>(1);
        env.put("foo", "bar");

        DockerFileBuilder builder =
                new DockerFileBuilder().add("/src", "/dest")
                        .baseImage("image")
                        .command("c1", "c2")
                        .env(env)
                        .basedir("/export")
                        .expose(Arrays.asList("8080"))
                        .maintainer("maintainer")
                        .volumes(Arrays.asList("/vol1"));

        String expected = loadFile("docker/Dockerfile.test");
        assertEquals(expected, stripCR(builder.content()));
    }

    @Test
    public void testNoRootExport() {
        assertFalse(new DockerFileBuilder().add("/src", "/dest").basedir("/").content().contains("VOLUME"));
    }
    
    private String stripCR(String input){
    	return input.replaceAll("\r", "");
    }

    private String loadFile(String fileName) throws IOException {
        return stripCR(IOUtils.toString(getClass().getClassLoader().getResource(fileName)));
    }
}
