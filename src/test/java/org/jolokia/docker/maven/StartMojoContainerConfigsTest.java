package org.jolokia.docker.maven;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.jolokia.docker.maven.access.Container;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.access.PortMapping;
import org.jolokia.docker.maven.config.RunImageConfiguration;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;


public class StartMojoContainerConfigsTest {

    private static final String BIND = "/tmp:/tmp";

    @Test
    @SuppressWarnings("unused")
    public void testCreateContainerAllConfig() throws Exception {
        /*-
         * this is really two tests in one
         *  - verify the start mojo calls all the methods to build the container configs
         *  - the container configs produce the correct json when all options are specified
         *  
         * it didn't seem worth the effor to build a separate test to verify the json and then mock/verify all the calls here
         */
        final RunImageConfiguration runConfig =
                new RunImageConfiguration.Builder()
                        .hostname("hostname")
                        .domainname("domain.com")
                        .user("user")
                        .memory(1)
                        .memorySwap(1)
                        .env(env())
                        .command("date")
                        .entrypoint("entrypoint")
                        .extraHosts(extraHosts())
                        .bind(bind())
                        .workingDir("/foo")
                        .ports(ports())
                        .links(links())
                        .volumes(volumesFrom())
                        .dns(dns()).dnsSearch(dnsSearch())
                        .privileged(true).capAdd(capAdd())
                        .capDrop(capDrop())
                        .restartPolicy(restartPolicy())
                        .build();

        StartMojo mojo = new StartMojo() {
            @Override
            List<String> findContainersForImages(List<String> images) throws MojoExecutionException {
                return images;
            }

            @Override
            List<String> findLinksWithContainerNames(DockerAccess docker, List<String> links) throws DockerAccessException {
                return links;
            }
        };

        PortMapping portMapping = mojo.getPortMapping(runConfig, new Properties());
        Container container = mojo.createContainer("base", null, runConfig, portMapping);

        String expectedConfig = loadFile("docker/createContainerAll.json");
        JSONAssert.assertEquals(expectedConfig, container.toCreateJson(), true);

        String expectedHostConfig = loadFile("docker/createHostConfigAll.json");
        JSONAssert.assertEquals(expectedHostConfig, container.toStartJson(), true);
    }

    private List<String> bind() {
        return Arrays.asList(BIND);
    }

    private List<String> capAdd() {
        return Arrays.asList("NET_ADMIN");
    }

    private List<String> capDrop() {
        return Arrays.asList("MKNOD");
    }

    private List<String> dns() {
        return Arrays.asList("8.8.8.8");
    }

    private List<String> dnsSearch() {
        return Arrays.asList("domain.com");
    }

    private Map<String, String> env() {
        Map<String, String> env = new HashMap<>();
        env.put("foo", "bar");

        return env;
    }
    
    private List<String> extraHosts()
    {
        return Arrays.asList("localhost:127.0.0.1");
    }

    private List<String> links() {
        return Arrays.asList("redis3:redis");
    }

    private String loadFile(String fileName) throws IOException {
        return IOUtils.toString(getClass().getClassLoader().getResource(fileName));
    }

    private List<String> ports() {
        return Arrays.asList("0.0.0.0:11022:22");
    }

    private RunImageConfiguration.RestartPolicy restartPolicy() {
        return new RunImageConfiguration.RestartPolicy("on-failure", 1);
    }

    private List<String> volumesFrom() {
        return Arrays.asList("parent", "other:ro");
    }
}
