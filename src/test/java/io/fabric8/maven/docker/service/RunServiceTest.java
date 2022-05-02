package io.fabric8.maven.docker.service;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.fabric8.maven.docker.access.ContainerCreateConfig;
import io.fabric8.maven.docker.access.ContainerHostConfig;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.ExecException;
import io.fabric8.maven.docker.access.PortMapping;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.NetworkConfig;
import io.fabric8.maven.docker.config.RestartPolicy;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.config.RunVolumeConfiguration;
import io.fabric8.maven.docker.config.StopMode;
import io.fabric8.maven.docker.config.UlimitConfig;
import io.fabric8.maven.docker.config.VolumeConfiguration;
import io.fabric8.maven.docker.config.WaitConfiguration;
import io.fabric8.maven.docker.log.LogOutputSpec;
import io.fabric8.maven.docker.log.LogOutputSpecFactory;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.model.ExecDetails;
import io.fabric8.maven.docker.model.PortBindingException;
import io.fabric8.maven.docker.util.GavLabel;
import io.fabric8.maven.docker.util.JsonFactory;
import io.fabric8.maven.docker.util.Logger;
import org.apache.commons.io.IOUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * This test need to be refactored. In fact, testing Mojos must be setup correctly at all. Blame on me that there are so
 * few tests ...
 */
@ExtendWith(MockitoExtension.class)
class RunServiceTest {

    private ContainerCreateConfig containerConfig;

    @Mock
    private MavenProject project;

    @Mock
    private MavenSession session;

    @Mock
    private DockerAccess docker;

    @Mock
    private Logger log;

    @Mock
    private QueryService queryService;

    private Properties properties;

    private RunImageConfiguration runConfig;

    private RunService runService;

    private ContainerHostConfig startConfig;

    private ContainerTracker tracker;

    @BeforeEach
    void setup() {
        tracker = new ContainerTracker();
        properties = new Properties();
        LogOutputSpecFactory logOutputSpecFactory = new LogOutputSpecFactory(true, true, null);

        runService = new RunService(docker, queryService, tracker, logOutputSpecFactory, log);
    }

    @Test
    void testCreateContainerAllConfig() throws IOException {
        /*-
         * this is really two tests in one
         *  - verify the start dockerRunner calls all the methods to build the container configs
         *  - the container configs produce the correct json when all options are specified
         *
         * it didn't seem worth the effort to build a separate test to verify the json and then mock/verify all the calls here
         */

        Mockito.doReturn("db1").when(queryService).getContainerName("redisContainer1");
        Mockito.doReturn("db2").when(queryService).getContainerName("redisContainer2");
        Mockito.doReturn("parentContainer").when(queryService).getContainerName("parentContainer");
        Mockito.doReturn("otherContainer").when(queryService).getContainerName("otherContainer");

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
    void shutdownWithoutKeepingContainers() throws DockerAccessException, ExecException {
        long start = System.currentTimeMillis();
        runService.stopContainer(container, createImageConfig(SHUTDOWN_WAIT, 0), false, false);
        Assertions.assertTrue(System.currentTimeMillis() - start >= SHUTDOWN_WAIT, "Waited for at least " + SHUTDOWN_WAIT + " ms");
        verifyStopAndRemove(0, false);
    }

    @Test
    void killafterAndShutdownWithoutKeepingContainers() throws DockerAccessException, ExecException {
        runService.stopContainer(container, createImageConfig(SHUTDOWN_WAIT, KILL_AFTER), false, false);
        verifyStopAndRemove((KILL_AFTER + 500) / 1000, false);
    }

    @Test
    void killafterWithoutKeepingContainers() throws DockerAccessException, ExecException {
        runService.stopContainer(container, createImageConfig(0, KILL_AFTER), false, false);
        verifyStopAndRemove((KILL_AFTER + 500) / 1000, false);
    }

    private void verifyStopAndRemove(int killWait, boolean removeVolumes) throws DockerAccessException {
        verifyStop(killWait);
        verifyRemove("Stop", removeVolumes);
    }

    private void verifyRemove(String mode, boolean removeVolumes) throws DockerAccessException {
        Mockito.verify(docker).removeContainer(container, removeVolumes);
        Assertions.assertTrue(verifyLog(mode).contains("removed"));
    }

    private void verifyStopAndKeep(int killWait) throws DockerAccessException {
        verifyStop(killWait);

        Assertions.assertFalse(verifyLog("Stop").contains(" and removed"));
    }

    private void verifyStop(int killWait) throws DockerAccessException {
        Mockito.verify(docker).stopContainer(container, killWait);
    }

    private String verifyLog(String mode) {
        ArgumentCaptor<String> formatCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> argsCaptor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(log).info(formatCaptor.capture(), argsCaptor.capture());

        Assertions.assertTrue(formatCaptor.getValue().contains(mode));
        List<Object> args = argsCaptor.getAllValues();
        Assertions.assertEquals(container.substring(0, 12), args.get(2));
        return (String)args.get(1);
    }

    @Test
    void shutdownWithoutKeepingContainersAndRemovingVolumes() throws DockerAccessException, ExecException {
        long start = System.currentTimeMillis();
        runService.stopContainer(container, createImageConfig(SHUTDOWN_WAIT, 0), false, true);
        Assertions.assertTrue(System.currentTimeMillis() - start >= SHUTDOWN_WAIT, "Waited for at least " + SHUTDOWN_WAIT + " ms");

        verifyStopAndRemove(0, true);
    }

    @Test
    void shutdownWithKeepingContainer() throws DockerAccessException, ExecException {
        long start = System.currentTimeMillis();
        runService.stopContainer(container, createImageConfig(SHUTDOWN_WAIT, 0), true, false);
        Assertions.assertTrue(System.currentTimeMillis() - start < SHUTDOWN_WAIT, "No wait");
        verifyStopAndKeep(0);
    }


    @Test
    void shutdownWithPreStopExecConfig() throws DockerAccessException, ExecException {
        Mockito.doReturn("execContainerId")
            .when(docker)
            .createExecContainer(Mockito.eq(container), Mockito.any(Arguments.class));

        Mockito.doReturn(new ExecDetails(JsonFactory.newJsonObject("{\"Running\":true}"))).when(docker).getExecContainer("execContainerId");

        long start = System.currentTimeMillis();
        runService.stopContainer(container, createImageConfigWithExecConfig(SHUTDOWN_WAIT), true, false);
        Assertions.assertTrue(System.currentTimeMillis() - start < SHUTDOWN_WAIT, "No wait");

        verifyStop(0);
    }

    @Test
    void testWithoutWait() throws DockerAccessException, ExecException {
        long start = System.currentTimeMillis();
        runService.stopContainer(container, createImageConfig(0, 0), false, false);
        Assertions.assertTrue(System.currentTimeMillis() - start < SHUTDOWN_WAIT, "No wait");
        verifyStopAndRemove(0, false);
    }

    @Test
    void testWithException() throws DockerAccessException {
        Mockito.doThrow(new DockerAccessException("Test")).when(docker).stopContainer(container, 0);
        ImageConfiguration imageConfig = createImageConfig(SHUTDOWN_WAIT, 0);
        Assertions.assertThrows(DockerAccessException.class, () -> runService.stopContainer(container, imageConfig, false, false));
    }

    @Test
    void testWithMultipleStopExceptions() throws DockerAccessException {
        GavLabel testLabel = new GavLabel("Im:A:Test");

        String firstName = "first-container:latest";
        ImageConfiguration first = new ImageConfiguration();
        first.setName(firstName);

        String secondName = "second-container:latest";
        ImageConfiguration second = new ImageConfiguration();
        second.setName(secondName);

        tracker.registerContainer(firstName, first, testLabel);
        tracker.registerContainer(secondName, second, testLabel);

        LogOutputSpecFactory logOutputSpecFactory = new LogOutputSpecFactory(true, true, null);

        Mockito.doThrow(new DockerAccessException("TEST one")).when(docker).stopContainer(firstName,0);
        Mockito.doThrow(new DockerAccessException("TEST two")).when(docker).stopContainer(secondName,0);

        runService = new RunService(docker, queryService, tracker, logOutputSpecFactory, log);

        Exception thrownException = Assertions.assertThrows(DockerAccessException.class, () -> runService.stopStartedContainers(false, true, true, testLabel));
        Assertions.assertEquals("(TEST two,TEST one)", thrownException.getLocalizedMessage());
    }

    @Test
    void testVolumesDuringStart() throws DockerAccessException {
        ServiceHub hub = new ServiceHubFactory().createServiceHub(project, session, docker, log, new LogOutputSpecFactory(true, true, null));
        List<String> volumeBinds = Collections.singletonList("sqlserver-backup-dev:/var/opt/mssql/data");
        List<VolumeConfiguration> volumeConfigurations = Collections.singletonList(volumeConfiguration);

        List<String> createdVolumes = runService.createVolumesAsPerVolumeBinds(hub, volumeBinds, volumeConfigurations);

        Assertions.assertEquals(createdVolumes.get(0), volumeConfigurations.get(0).getName());
        Assertions.assertTrue(createdVolumes.contains(volumeConfigurations.get(0).getName()));
    }

    @Test
    void testStopModeWithKill() throws DockerAccessException, ExecException {
        long start = System.currentTimeMillis();
        runService.stopContainer(container, createImageConfigWithStopMode(), false, false);
        Assertions.assertTrue(System.currentTimeMillis() - start < SHUTDOWN_WAIT, "No wait");

        Mockito.verify(docker).killContainer(container);
        verifyRemove("Killed", false);

    }

    @Test
    void retryIfInsufficientPortBindingInformation(
        @Mock Container container,
        @Mock ImageConfiguration imageConfiguration,
        @Mock PortMapping portMapping
    ) throws DockerAccessException {

        Mockito.doReturn("containerId")
            .when(docker).createContainer(Mockito.any(ContainerCreateConfig.class), Mockito.anyString());

        Mockito.doReturn(true).when(portMapping).needsPropertiesUpdate();
        Mockito.doReturn(container).when(queryService).getMandatoryContainer("containerId");
        Mockito.doReturn(true).when(container).isRunning();
        Mockito.doReturn(new RunImageConfiguration()).when(imageConfiguration).getRunConfiguration();

        Mockito.doThrow(new PortBindingException("5432/tcp", new Gson().fromJson("{\"5432/tcp\": []}", JsonObject.class)))
            .doReturn(ImmutableMap.of("5432/tcp", new Container.PortBinding(56741, "0.0.0.0")))
            .when(container).getPortBindings();

        String containerId = runService.createAndStartContainer(imageConfiguration, portMapping, new GavLabel("Im:A:Test"), properties, getBaseDirectory(), "blah", new Date());
        Assertions.assertEquals("containerId", containerId);
    }

    @Test
    void failAfterRetryingIfInsufficientPortBindingInformation(
        @Mock Container container,
        @Mock ImageConfiguration imageConfiguration,
        @Mock PortMapping portMapping
    ) throws DockerAccessException {

        Mockito.doReturn("containerId")
            .when(docker).createContainer(Mockito.any(ContainerCreateConfig.class), Mockito.anyString());

        Mockito.doReturn(true).when(portMapping).needsPropertiesUpdate();
        Mockito.doReturn(container).when(queryService).getMandatoryContainer("containerId");
        Mockito.doReturn(true).when(container).isRunning();
        Mockito.doReturn(new RunImageConfiguration()).when(imageConfiguration).getRunConfiguration();
        Mockito.doThrow(new PortBindingException("5432/tcp", new Gson().fromJson("{\"5432/tcp\": []}", JsonObject.class)))
            .when(container).getPortBindings();

        GavLabel gavLabel = new GavLabel("Im:A:Test");
        File baseDirectory = getBaseDirectory();
        Date buildTimestamp = new Date();
        Assertions.assertThrows(PortBindingException.class,
            () -> runService.createAndStartContainer(imageConfiguration, portMapping, gavLabel, properties, baseDirectory, "blah", buildTimestamp));
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

    private ImageConfiguration createImageConfigWithStopMode() {
        return new ImageConfiguration.Builder()
            .name("test_name")
            .alias("testAlias")
            .runConfig(new RunImageConfiguration.Builder()
                .stopMode(StopMode.kill)
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

    private void addToTracker(String varName, String key, String value) {
        try {
            Field field = tracker.getClass().getDeclaredField(varName);
            field.setAccessible(true);
            Map<String, String> map = (Map<String, String>) field.get(tracker);
            map.put(key, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    // Better than poking into the private vars would be to use createAndStart() with the mock to build up the map.
    private void givenAnImageConfiguration(String name, String alias, String containerId)  {
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
                .isolation("default")
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
                .sysctls(sysctls())
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
        Assertions.assertEquals(expectedConfig.toString(), containerConfig.toJson());
    }

    private void thenStartConfigIsValid() throws IOException {
        JsonObject expectedHostConfig = JsonFactory.newJsonObject(loadFile("docker/containerHostConfigAll.json"));
        Assertions.assertEquals(expectedHostConfig.toString(), startConfig.toJson());
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

    private Map<String, String> sysctls() {
        return Collections.singletonMap("key", "value");
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

    private List<UlimitConfig> ulimits() {
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
}
