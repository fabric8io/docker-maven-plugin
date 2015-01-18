package org.jolokia.docker.maven;

import java.io.IOException;
import java.util.*;

import mockit.*;
import org.apache.commons.io.IOUtils;
import org.jolokia.docker.maven.access.*;
import org.jolokia.docker.maven.config.*;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;


/**
 * This test need to be refactored. In fact, testing Mojos must be setup correctly
 * at all. Blame on me that there are so few tests ...
 */
public class StartMojoContainerConfigsTest {

    @Mocked
    DockerAccess docker;

    @Test
    @SuppressWarnings("unused")
    public void testCreateContainerAllConfig() throws Exception {
        /*-
         * this is really two tests in one
         *  - verify the start mojo calls all the methods to build the container configs
         *  - the container configs produce the correct json when all options are specified
         *  
         * it didn't seem worth the effort to build a separate test to verify the json and then mock/verify all the calls here
         */

        VolumeConfiguration volumeConfiguration =
                new VolumeConfiguration.Builder()
                        .bind(bind())
                        .from(volumesFrom())
                        .build();
        final RunImageConfiguration runConfig =
                new RunImageConfiguration.Builder()
                        .hostname("hostname")
                        .domainname("domain.com")
                        .user("user")
                        .memory(1L)
                        .memorySwap(1L)
                        .env(env())
                        .command("date")
                        .entrypoint("entrypoint")
                        .extraHosts(extraHosts())
                        .workingDir("/foo")
                        .ports(ports())
                        .links(links())
                        .volumes(volumeConfiguration)
                        .dns(dns()).dnsSearch(dnsSearch())
                        .privileged(true).capAdd(capAdd())
                        .capDrop(capDrop())
                        .restartPolicy(restartPolicy())
                        .build();

        StartMojo mojo = new StartMojo();
        PortMapping portMapping = mojo.getPortMapping(runConfig, new Properties());

        new Expectations() {{
            docker.getContainerName((String) withNotNull());
            result = "redis";
            minTimes = 1;

        }};

        mojo.registerContainer("redisContainer", new ImageConfiguration.Builder().alias("db").name("redis3").build());
        mojo.registerContainer("parentContainer", new ImageConfiguration.Builder().alias("parent").name("parentName").build());
        mojo.registerContainer("otherContainer", new ImageConfiguration.Builder().alias("other:ro").name("otherName").build());
        ContainerCreateConfig containerConfig = mojo.createContainerConfig(docker, "base", runConfig, portMapping);

        String expectedConfig = loadFile("docker/containerCreateConfigAll.json");
        JSONAssert.assertEquals(expectedConfig, containerConfig.toJson(), true);

        ContainerHostConfig startConfig = mojo.createContainerHostConfig(docker, runConfig, portMapping);
        String expectedHostConfig = loadFile("docker/containerHostConfigAll.json");
        JSONAssert.assertEquals(expectedHostConfig, startConfig.toJson(), true);
    }

    private List<String> bind() {
        return Arrays.asList("/host_tmp:/container_tmp");
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

    private List<String> extraHosts() {
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

    private RestartPolicy restartPolicy() {
        return new RestartPolicy.Builder().name("on-failure").retry(1).build();
    }

    private List<String> volumesFrom() {
        return Arrays.asList("parent", "other:ro");
    }
}

