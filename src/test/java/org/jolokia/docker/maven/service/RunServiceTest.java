package org.jolokia.docker.maven.service;

import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.maven.project.MavenProject;
import org.jolokia.docker.maven.access.ContainerCreateConfig;
import org.jolokia.docker.maven.access.ContainerHostConfig;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.access.PortMapping;
import org.jolokia.docker.maven.config.RestartPolicy;
import org.jolokia.docker.maven.config.RunImageConfiguration;
import org.jolokia.docker.maven.config.VolumeConfiguration;
import org.jolokia.docker.maven.util.Logger;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.skyscreamer.jsonassert.JSONAssert;

/**
 * This test need to be refactored. In fact, testing Mojos must be setup correctly at all. Blame on me that there are so
 * few tests ...
 */
public class RunServiceTest {

    private ContainerCreateConfig containerConfig;

    @Mock
    private DockerAccess docker;

    @Mock
    private Logger log;

    @Mock
    private MavenProject mavenProject;

    private Properties properties;

    @Mock
    private QueryService queryService;

    private RunImageConfiguration runConfig;

    private RunService runService;

    private ContainerHostConfig startConfig;

    private ContainerTracker tracker;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        tracker = new ContainerTracker();
        properties = new Properties();

        runService = new RunService(docker, queryService, tracker, log);
    }

    @Test
    public void testCreateContainerAllConfig() throws Exception {
        /*-
         * this is really two tests in one
         *  - verify the start dockerRunner calls all the methods to build the container configs
         *  - the container configs produce the correct json when all options are specified
         *  
         * it didn't seem worth the effort to build a separate test to verify the json and then mock/verify all the calls here
         */

        givenARunConfiguration();
        givenAnImageConfiguration("redis3", "db1", "redisContainer1");
        givenAnImageConfiguration("redis3", "db2", "redisContainer2");
        
        givenAnImageConfiguration("parent", "parentName", "parentContainer");
        givenAnImageConfiguration("otherName", "other:ro", "otherContainer");

        whenQueryForContainerName("redisContainer1", "db1");
        whenQueryForContainerName("redisContainer2", "db2");
        whenQueryForContainerName("parentContainer", "parentContainer");
        whenQueryForContainerName("otherContainer", "otherContainer");
        whenCreateContainerConfig("base");

        thenContainerConfigIsValid();
        thenStartConfigIsValid();
    }

    private void addToTracker(String varName, String key, String value) throws NoSuchFieldException, IllegalAccessException {
        Field field = tracker.getClass().getDeclaredField(varName);
        field.setAccessible(true);
        Map<String, String> map = (Map<String, String>) field.get(tracker);
        map.put(key, value);
    }
    
    // Better than poking into the private vars would be to use createAndStart() with the mock to build up the map.
    private void givenAnImageConfiguration(String name, String alias, String containerId) throws Exception {
        addToTracker("imageToContainerMap", name, containerId);
        addToTracker("aliasToContainerMap", alias, containerId);
    }

    private void givenARunConfiguration() {
        runConfig =
                new RunImageConfiguration.Builder()
                        .hostname("hostname")
                        .domainname("domain.com")
                        .user("user")
                        .memory(1L)
                        .memorySwap(1L)
                        .env(env())
                        .cmd("date")
                        .entrypoint("entrypoint")
                        .extraHosts(extraHosts())
                        .workingDir("/foo")
                        .ports(ports())
                        .links(links())
                        .volumes(volumeConfiguration())
                        .dns(dns())
                        .dnsSearch(dnsSearch())
                        .privileged(true)
                        .capAdd(capAdd())
                        .capDrop(capDrop())
                        .restartPolicy(restartPolicy())
                        .build();
    }

    private void thenContainerConfigIsValid() throws IOException {
        String expectedConfig = loadFile("docker/containerCreateConfigAll.json");
        JSONAssert.assertEquals(expectedConfig, containerConfig.toJson(), true);
    }

    private void thenStartConfigIsValid() throws IOException {
        String expectedHostConfig = loadFile("docker/containerHostConfigAll.json");
        JSONAssert.assertEquals(expectedHostConfig, startConfig.toJson(), true);
    }

    private void whenCreateContainerConfig(String imageName) throws DockerAccessException {
        PortMapping portMapping = runService.getPortMapping(runConfig, properties);

        containerConfig = runService.createContainerConfig(imageName, runConfig, portMapping, properties);
        startConfig = runService.createContainerHostConfig(runConfig, portMapping);
    }

    private void whenQueryForContainerName(String containerId, String name) throws DockerAccessException {
        when(queryService.getContainerName(containerId)).thenReturn(name);
    }

    private List<String> bind() {
        return Collections.singletonList("/host_tmp:/container_tmp");
    }

    private List<String> capAdd() {
        return Collections.singletonList("NET_ADMIN");
    }

    private List<String> capDrop() {
        return Collections.singletonList("MKNOD");
    }

    private List<String> dns() {
        return Collections.singletonList("8.8.8.8");
    }

    private List<String> dnsSearch() {
        return Collections.singletonList("domain.com");
    }

    private Map<String, String> env() {
        Map<String, String> env = new HashMap<>();
        env.put("foo", "bar");

        return env;
    }

    private List<String> extraHosts() {
        return Collections.singletonList("localhost:127.0.0.1");
    }

    private List<String> links() {
        return Collections.unmodifiableList(Arrays.asList("db1", "db2"));
    }

    private String loadFile(String fileName) throws IOException {
        return IOUtils.toString(getClass().getClassLoader().getResource(fileName));
    }

    private List<String> ports() {
        return Collections.singletonList("0.0.0.0:11022:22");
    }

    private RestartPolicy restartPolicy() {
        return new RestartPolicy.Builder().name("on-failure").retry(1).build();
    }

    private VolumeConfiguration volumeConfiguration() {
        return new VolumeConfiguration.Builder()
                .bind(bind())
                .from(volumesFrom())
                .build();
    }

    private List<String> volumesFrom() {
        return Arrays.asList("parent", "other:ro");
    }
}
