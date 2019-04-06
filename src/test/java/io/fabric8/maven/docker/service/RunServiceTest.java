package io.fabric8.maven.docker.service;

import com.google.gson.JsonObject;

import io.fabric8.maven.docker.config.VolumeConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.fabric8.maven.docker.access.ContainerCreateConfig;
import io.fabric8.maven.docker.access.ContainerHostConfig;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.PortMapping;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.NetworkConfig;
import io.fabric8.maven.docker.config.RestartPolicy;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.config.RunVolumeConfiguration;
import io.fabric8.maven.docker.config.UlimitConfig;
import io.fabric8.maven.docker.config.WaitConfiguration;
import io.fabric8.maven.docker.log.LogOutputSpec;
import io.fabric8.maven.docker.log.LogOutputSpecFactory;
import io.fabric8.maven.docker.util.JsonFactory;
import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.Mocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * This test need to be refactored. In fact, testing Mojos must be setup correctly at all. Blame on me that there are so
 * few tests ...
 */
public class RunServiceTest {

    private ContainerCreateConfig containerConfig;

    @Mocked
    private MavenProject project;

    @Mocked
    private MavenSession session;

    @Mocked
    private DockerAccess docker;

    @Mocked
    private Logger log;

    @Mocked
    private QueryService queryService;

    private Properties properties;

    private RunImageConfiguration runConfig;

    private RunService runService;

    private ContainerHostConfig startConfig;

    private ContainerTracker tracker;

    @Before
    public void setup() {
        tracker = new ContainerTracker();
        properties = new Properties();
        LogOutputSpecFactory logOutputSpecFactory = new LogOutputSpecFactory(true, true, null);

        runService = new RunService(docker, queryService, tracker, logOutputSpecFactory, log);
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

        new Expectations() {{
            queryService.getContainerName("redisContainer1"); result = "db1";
            queryService.getContainerName("redisContainer2"); result = "db2";
            queryService.getContainerName("parentContainer"); result = "parentContainer";
            queryService.getContainerName("otherContainer"); result = "otherContainer";
        }};

        givenARunConfiguration();
        givenAnImageConfiguration("redis3", "db1", "redisContainer1");
        givenAnImageConfiguration("redis3", "db2", "redisContainer2");

        givenAnImageConfiguration("parent", "parentName", "parentContainer");
        givenAnImageConfiguration("other_name", "other:ro", "otherContainer");


        whenCreateContainerConfig("base");

        thenContainerConfigIsValid();
        thenStartConfigIsValid();
    }

    // ===========================================================

    private String container = "testContainer";
    private int SHUTDOWN_WAIT = 500;
    private int KILL_AFTER = 1000;
    private VolumeConfiguration volumeConfiguration = new VolumeConfiguration.Builder()
            .name("sqlserver-backup-dev")
            .driver("rexray")
            .opts(Collections.singletonMap("size", "50"))
            .build();

    @Test
    public void shutdownWithoutKeepingContainers() throws Exception {
        new Expectations() {{
            docker.stopContainer(container, 0);
            log.debug(anyString, (Object[]) any); minTimes = 1;
            docker.removeContainer(container, false);
            log.info(withSubstring("Stop"),
                     anyString,
                     withSubstring("removed"),
                     withSubstring(container.substring(0,12)),
                     anyLong);
        }};

        long start = System.currentTimeMillis();
        runService.stopContainer(container, createImageConfig(SHUTDOWN_WAIT, 0), false, false);
        assertTrue("Waited for at least " + SHUTDOWN_WAIT + " ms",
                System.currentTimeMillis() - start >= SHUTDOWN_WAIT);
    }
    @Test
    @Ignore
    public void killafterAndShutdownWithoutKeepingContainers() throws Exception {
        long start = System.currentTimeMillis();
        setupForKillWait();

        runService.stopContainer(container, createImageConfig(SHUTDOWN_WAIT, KILL_AFTER), false, false);
        assertTrue("Waited for at least " + (SHUTDOWN_WAIT + KILL_AFTER) + " ms",
                System.currentTimeMillis() - start >= SHUTDOWN_WAIT + KILL_AFTER);
    }

    @Test
    @Ignore
    public void killafterWithoutKeepingContainers() throws Exception {
        long start = System.currentTimeMillis();
        setupForKillWait();

        runService.stopContainer(container, createImageConfig(0, KILL_AFTER), false, false);
        assertTrue("Waited for at least " + (KILL_AFTER) + " ms",
                   System.currentTimeMillis() - start >= KILL_AFTER);
    }

    private void setupForKillWait() throws DockerAccessException {
        // use this to simulate something happened - timers need to be started before this method gets invoked
        // This used to work:
        // docker = new MockUp<DockerAccess>() {
        //    @Mock
        //    public void stopContainer(String contaierId, int wait) {
        //        WaitUtil.sleep(KILL_AFTER);
        //    }
        ///}.getMockInstance();

        new Expectations() {{

            docker.stopContainer(container, (KILL_AFTER + 500) / 1000);
            log.debug(anyString, (Object[]) any); minTimes = 1;
            docker.removeContainer(container, false);
            log.info(withSubstring("Stop"),
                     anyString,
                     withSubstring("removed"),
                     withSubstring(container.substring(0,12)),
                     anyLong);
        }};
    }

    @Test
    public void shutdownWithoutKeepingContainersAndRemovingVolumes() throws Exception {
        new Expectations() {{

            docker.stopContainer(container, 0);
            log.debug(anyString, (Object[]) any); minTimes = 1;
            docker.removeContainer(container, true);
            log.info(withSubstring("Stop"),
                     anyString,
                     withSubstring("removed"),
                     withSubstring(container.substring(0,12)),
                     anyLong);
        }};

        long start = System.currentTimeMillis();
        runService.stopContainer(container, createImageConfig(SHUTDOWN_WAIT, 0), false, true);
        assertTrue("Waited for at least " + SHUTDOWN_WAIT + " ms",
                   System.currentTimeMillis() - start >= SHUTDOWN_WAIT);
    }

    @Test
    public void shutdownWithKeepingContainer() throws Exception {
        new Expectations() {{
            docker.stopContainer(container, 0);
            log.info(withSubstring("Stop"),
                     anyString,
                     withNotEqual(" and removed"),
                     withSubstring(container.substring(0,12)),
                     anyLong);
        }};

        long start = System.currentTimeMillis();
        runService.stopContainer(container, createImageConfig(SHUTDOWN_WAIT, 0), true, false);
        assertTrue("No wait",
                   System.currentTimeMillis() - start < SHUTDOWN_WAIT);

    }

    @Test
    public void shutdownWithPreStopExecConfig() throws Exception {

        new Expectations() {{
            docker.createExecContainer(container, (Arguments) withNotNull());result = "execContainerId";
            docker.startExecContainer("execContainerId", (LogOutputSpec) any);
            docker.stopContainer(container, 0);
            log.info(withSubstring("Stop"),
                     anyString,
                     withNotEqual(" and removed"),
                     withSubstring(container.substring(0,12)),
                     anyLong);
        }};
        long start = System.currentTimeMillis();
        runService.stopContainer(container, createImageConfigWithExecConfig(SHUTDOWN_WAIT), true, false);
        assertTrue("No wait",
                   System.currentTimeMillis() - start < SHUTDOWN_WAIT);
    }

    @Test
    public void testWithoutWait() throws Exception {
        new Expectations() {{
            docker.stopContainer(container, 0);
            log.debug(anyString); times = 0;
            docker.removeContainer(container, false);
            log.info(withSubstring("Stop"),
                     anyString,
                     withSubstring("removed"),
                     withSubstring(container.substring(0,12)),
                     anyLong);
        }};

        long start = System.currentTimeMillis();
        runService.stopContainer(container, createImageConfig(0, 0), false, false);
        assertTrue("No wait", System.currentTimeMillis() - start < SHUTDOWN_WAIT);
    }

    @Test(expected = DockerAccessException.class)
    public void testWithException() throws Exception {

        new Expectations() {{
            docker.stopContainer(container, 0); result = new DockerAccessException("Test");
        }};

        runService.stopContainer(container, createImageConfig(SHUTDOWN_WAIT, 0), false, false);
    }

    @Test
    public void testVolumesDuringStart() throws DockerAccessException {
        ServiceHub hub = new ServiceHubFactory().createServiceHub(project, session, docker, log, new LogOutputSpecFactory(true, true, null));
        List<String> volumeBinds = Collections.singletonList("sqlserver-backup-dev:/var/opt/mssql/data");
        List<VolumeConfiguration> volumeConfigurations = Collections.singletonList(volumeConfiguration);

        List<String> createdVolumes = runService.createVolumesAsPerVolumeBinds(hub, volumeBinds, volumeConfigurations);

        assertEquals(createdVolumes.get(0), volumeConfigurations.get(0).getName());
        assertTrue(createdVolumes.contains(volumeConfigurations.get(0).getName()));
    }

    private ImageConfiguration createImageConfig(int wait, int kill) {
        return new ImageConfiguration.Builder()
                .name("test_name")
                .alias("testAlias")
                .runConfig(new RunImageConfiguration.Builder()
                                   .wait(new WaitConfiguration.Builder()
                                                 .shutdown(wait)
                                                 .kill(kill)
                                                 .build())
                                   .build())
                .build();
    }

    private ImageConfiguration createImageConfigWithExecConfig(int wait) {
        return new ImageConfiguration.Builder()
                .name("test_name")
                .alias("testAlias")
                .runConfig(new RunImageConfiguration.Builder()
                                   .wait(new WaitConfiguration.Builder()
                                                 .shutdown(wait)
                                                 .preStop("pre-stop-command")
                                                 .postStart("post-start-command")
                                                 .build())
                                   .build())
                .build();
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
                        .shmSize(1024L)
                        .memory(1L)
                        .memorySwap(1L)
                        .cpus(1000000000L)
                        .cpuSet("0,1")
                        .cpuShares(1L)
                        .env(env())
                        .cmd("date")
                        .entrypoint(new Arguments("entrypoint"))
                        .extraHosts(extraHosts())
                        .ulimits(ulimits())
                        .workingDir("/foo")
                        .ports(ports())
                        .links(links())
                        .volumes(volumeConfiguration())
                        .dns(dns())
                        .dnsSearch(dnsSearch())
                        .privileged(true)
                        .capAdd(capAdd())
                        .capDrop(capDrop())
                        .securityOpts(securityOpts())
                        .restartPolicy(restartPolicy())
                        .net("custom_network")
                        .network(networkConfiguration())
                        .readOnly(false)
                        .autoRemove(false)
                        .build();
    }

    private NetworkConfig networkConfiguration() {
        NetworkConfig config = new NetworkConfig("custom_network");
        config.addAlias("net-alias");
        return config;
    }
    private void thenContainerConfigIsValid() throws IOException {
        JsonObject expectedConfig = JsonFactory.newJsonObject(loadFile("docker/containerCreateConfigAll.json"));
        assertEquals(expectedConfig.toString(), containerConfig.toJson());
    }

    private void thenStartConfigIsValid() throws IOException {
        JsonObject expectedHostConfig = JsonFactory.newJsonObject(loadFile("docker/containerHostConfigAll.json"));
        assertEquals(expectedHostConfig.toString(), startConfig.toJson());
    }

    private void whenCreateContainerConfig(String imageName) throws DockerAccessException {
        PortMapping portMapping = runService.createPortMapping(runConfig, properties);

        containerConfig = runService.createContainerConfig(imageName, runConfig, portMapping, null, properties, getBaseDirectory());
        startConfig = runService.createContainerHostConfig(runConfig, portMapping, getBaseDirectory());
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

    private List<String> securityOpts() {
        return Collections.singletonList("seccomp=unconfined");
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
    private List<UlimitConfig> ulimits(){
        return Collections.singletonList(new UlimitConfig("memlock=1024:2048"));
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

    private File getBaseDirectory() {
        return new File(getClass().getResource("/").getPath());
    }

    private List<String> ports() {
        return Collections.singletonList("0.0.0.0:11022:22");
    }

    private RestartPolicy restartPolicy() {
        return new RestartPolicy.Builder().name("on-failure").retry(1).build();
    }

    private RunVolumeConfiguration volumeConfiguration() {
        return new RunVolumeConfiguration.Builder()
                .bind(bind())
                .from(volumesFrom())
                .build();
    }

    private List<String> volumesFrom() {
        return Arrays.asList("parent", "other:ro");
    }

    final class LogInfoMatchingExpectations extends Expectations {
        LogInfoMatchingExpectations(String container, boolean withRemove) {
            log.info(withSubstring("Stop"),
                     anyString,
                     withRemove ? withSubstring("removed") : withNotEqual(" and removed"),
                     withSubstring(container.substring(0,12)),
                     anyLong);
        }
    }

}
