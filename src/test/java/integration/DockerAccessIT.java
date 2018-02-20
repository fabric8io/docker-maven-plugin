package integration;

import com.google.common.collect.Lists;
import io.fabric8.maven.docker.access.ContainerCreateConfig;
import io.fabric8.maven.docker.access.ContainerHostConfig;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.DockerConnectionDetector;
import io.fabric8.maven.docker.access.DockerMachine;
import io.fabric8.maven.docker.access.PortMapping;
import io.fabric8.maven.docker.access.hc.DockerAccessWithHcClient;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.DockerMachineConfiguration;
import io.fabric8.maven.docker.model.Container.PortBinding;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.MAC;

/*
 * if run from your ide, this test assumes you have configured the runner w/ the appropriate env variables
 *
 * it also assumes that 'removeImage' does what it's supposed to do as it's used in test setup.
 */
@Disabled("debugging aid")
@EnabledOnOs({ LINUX, MAC })
class DockerAccessIT {

    private static final String CONTAINER_NAME = "integration-test";
    private static final String IMAGE = "busybox:buildroot-2014.02";

    private static final String IMAGE_TAG = "busybox:tagged";
    private static final String IMAGE_LATEST = "busybox:latest";
    private static final int PORT = 5677;

    private String containerId;
    private final DockerAccessWithHcClient dockerClient;

    public DockerAccessIT() throws IOException {
        AnsiLogger logger = new AnsiLogger(new SystemStreamLog(), true, "build");
        String url = createDockerConnectionDetector(logger).detectConnectionParameter(null,null).getUrl();
        this.dockerClient = createClient(url, logger);
    }

    private DockerConnectionDetector createDockerConnectionDetector(Logger logger) {
        DockerMachineConfiguration machine = new DockerMachineConfiguration("default","false","false");
        return new DockerConnectionDetector(
            Collections.singletonList(new DockerMachine(logger, machine)));
    }

    @BeforeEach
    void setup() throws DockerAccessException {
        // yes - this is a big don't do for tests
        testRemoveImage(IMAGE);
        testRemoveImage(IMAGE_TAG);
    }

    @Test
    void testPullStartStopRemove() throws DockerAccessException, InterruptedException {
        testDoesNotHave();

        try {
            testPullImage();
            testTagImage();
            testCreateContainer();
            testStartContainer();
            testExecContainer();
            testQueryPortMapping();
            testStopContainer();
            Thread.sleep(2000);
            testRemoveContainer();
        } finally {
            testRemoveImage(IMAGE);
        }
    }

    @Test
    void testLoadImage() throws DockerAccessException {
        testDoesNotHave();
        dockerClient.loadImage(IMAGE_LATEST, new File("integration/busybox-image-test.tar.gz"));
        Assertions.assertTrue(hasImage(IMAGE_LATEST));
        testRemoveImage(IMAGE_LATEST);
    }

    private DockerAccessWithHcClient createClient(String baseUrl, Logger logger) {
        try {
            String certPath = createDockerConnectionDetector(logger).detectConnectionParameter(null,null).getCertPath();
            return new DockerAccessWithHcClient(baseUrl, certPath, 20, logger);
        } catch (@SuppressWarnings("unused") IOException e) {
            // not using ssl, so not going to happen
            logger.error(e.getMessage());
            throw new RuntimeException();
        }
    }

    private void testCreateContainer() throws DockerAccessException {
        PortMapping portMapping = new PortMapping(Collections.singletonList(PORT + ":" + PORT), new Properties());
        ContainerHostConfig hostConfig = new ContainerHostConfig().portBindings(portMapping);
        ContainerCreateConfig createConfig = new ContainerCreateConfig(IMAGE).command(new Arguments("ping google.com")).hostConfig(hostConfig);

        containerId = dockerClient.createContainer(createConfig, CONTAINER_NAME);
        Assertions.assertNotNull(containerId);

        String name = dockerClient.getContainer(containerId).getName();
        Assertions.assertEquals(CONTAINER_NAME, name);
    }

    private void testDoesNotHave() throws DockerAccessException {
        Assertions.assertFalse(hasImage(IMAGE));
    }

    private void testPullImage() throws DockerAccessException {
        dockerClient.pullImage(IMAGE, null, null, null, 0);
        Assertions.assertTrue(hasImage(IMAGE));
    }

    private void testQueryPortMapping() throws DockerAccessException {
        Map<String, PortBinding> portMap = dockerClient.getContainer(containerId).getPortBindings();
        Assertions.assertEquals(5677, portMap.values().iterator().next().getHostPort().intValue());
    }

    private void testRemoveContainer() throws DockerAccessException {
        dockerClient.removeContainer(containerId, true);
    }

    private void testRemoveImage(String image) throws DockerAccessException {
        dockerClient.removeImage(image, false);
        Assertions.assertFalse(hasImage(image));
    }

    private void testStartContainer() throws DockerAccessException {
        dockerClient.startContainer(containerId);
        Assertions.assertTrue(dockerClient.getContainer(containerId).isRunning());
    }

    private void testExecContainer() throws DockerAccessException {
        Arguments arguments = new Arguments();
        arguments.setExec(Lists.newArrayList("echo", "test", "echo"));
        String execContainerId = dockerClient.createExecContainer(this.containerId, arguments);
        //assertThat(dockerClient.startExecContainer(execContainerId), is("test echo"));
    }

    private void testStopContainer() throws DockerAccessException {
        dockerClient.stopContainer(containerId, 0);
        Assertions.assertFalse(dockerClient.getContainer(containerId).isRunning());
    }

    private void testTagImage() throws DockerAccessException {
        dockerClient.tag(IMAGE, IMAGE_TAG,false);
        Assertions.assertTrue(hasImage(IMAGE_TAG));

        dockerClient.removeImage(IMAGE_TAG, false);
        Assertions.assertFalse(hasImage(IMAGE_TAG));
    }

    private boolean hasImage(String image) throws DockerAccessException {
        return dockerClient.hasImage(image);
    }
}
