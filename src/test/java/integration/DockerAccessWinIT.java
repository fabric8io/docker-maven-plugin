package integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import io.fabric8.maven.docker.access.*;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.Lists;

import io.fabric8.maven.docker.AbstractDockerMojo;
import io.fabric8.maven.docker.access.hc.DockerAccessWithHcClient;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.model.Container.PortBinding;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.Logger;

/*
 * if run from your ide, this test assumes you have configured the runner w/ the appropriate env variables
 *
 * it also assumes that 'removeImage' does what it's supposed to do as it's used in test setup.
 */
@Ignore
public class DockerAccessWinIT {

    private static final String CONTAINER_NAME = "integration-test";
    private static final String IMAGE = "busybox:buildroot-2014.02";

    private static final String IMAGE_TAG = "busybox:tagged";
    private static final int PORT = 5677;

    private String containerId;
    private final DockerAccessWithHcClient dockerClient;

    public DockerAccessWinIT() throws IOException {
        AnsiLogger logger = new AnsiLogger(new SystemStreamLog(), true, true);
        String url = createDockerConnectionDetector(logger).detectConnectionParameter(null, null).getUrl();
        this.dockerClient = createClient(url, logger);
    }

    private DockerConnectionDetector createDockerConnectionDetector(Logger logger) {
        return new DockerConnectionDetector(
            Collections.<DockerConnectionDetector.DockerHostProvider>singletonList(new DockerMachine(logger, null)));
    }

    @Before
    public void setup() throws DockerAccessException {
        // yes - this is a big don't do for tests
        testRemoveImage(IMAGE);
        testRemoveImage(IMAGE_TAG);
    }

    @Test
    @Ignore
    public void testBuildImage() throws DockerAccessException {
        File file = new File("src/test/resources/integration/busybox-test.tar");
        dockerClient.buildImage(IMAGE_TAG, file, null, false, false, Collections.<String, String>emptyMap(), Collections.<String, String>emptyMap());
        assertTrue(hasImage(IMAGE_TAG));

        testRemoveImage(IMAGE_TAG);
    }

    @Test
    public void testPullStartStopRemove() throws DockerAccessException {
        testDoesNotHave();

        try {
            testPullImage();
            testTagImage();
            testCreateContainer();
            testStartContainer();
            testExecContainer();
            testQueryPortMapping();
            testStopContainer();
            testRemoveContainer();
        } finally {
            testRemoveImage(IMAGE);
        }
    }

    private DockerAccessWithHcClient createClient(String baseUrl, Logger logger) {
        try {
            String certPath = createDockerConnectionDetector(logger).detectConnectionParameter(null, null).getCertPath();
            return new DockerAccessWithHcClient("v" + AbstractDockerMojo.API_VERSION, baseUrl, certPath, 1, logger);
        } catch (@SuppressWarnings("unused") IOException e) {
            // not using ssl, so not going to happen
            logger.error(e.getMessage());
            throw new RuntimeException();
        }
    }

    private void testCreateContainer() throws DockerAccessException {
        PortMapping portMapping = new PortMapping(Arrays.asList(new String[] {PORT + ":" + PORT }), new Properties());
        ContainerHostConfig hostConfig = new ContainerHostConfig().portBindings(portMapping);
        ContainerCreateConfig createConfig = new ContainerCreateConfig(IMAGE).command(new Arguments("ping google.com")).hostConfig(hostConfig);

        containerId = dockerClient.createContainer(createConfig, CONTAINER_NAME);
        assertNotNull(containerId);

        String name = dockerClient.getContainer(containerId).getName();
        assertEquals(CONTAINER_NAME, name);
    }

    private void testDoesNotHave() throws DockerAccessException {
        assertFalse(hasImage(IMAGE));
    }

    private void testPullImage() throws DockerAccessException {
        dockerClient.pullImage(IMAGE, null, null);
        assertTrue(hasImage(IMAGE));
    }

    private void testQueryPortMapping() throws DockerAccessException {
        Map<String, PortBinding> portMap = dockerClient.getContainer(containerId).getPortBindings();
        assertEquals(5677, portMap.values().iterator().next().getHostPort().intValue());
    }

    private void testRemoveContainer() throws DockerAccessException {
        dockerClient.removeContainer(containerId, true);
    }

    private void testRemoveImage(String image) throws DockerAccessException {
        dockerClient.removeImage(image, false);
        assertFalse(hasImage(image));
    }

    private void testStartContainer() throws DockerAccessException {
        dockerClient.startContainer(containerId);
        assertTrue(dockerClient.getContainer(containerId).isRunning());
    }

    private void testExecContainer() throws DockerAccessException {
        Arguments arguments = new Arguments();
        arguments.setExec(Lists.newArrayList("echo", "test", "echo"));
        String execContainerId = dockerClient.createExecContainer(this.containerId, arguments);
        //assertThat(dockerClient.startExecContainer(execContainerId), is("test echo"));
    }

    private void testStopContainer() throws DockerAccessException {
        dockerClient.stopContainer(containerId, 0);
        assertFalse(dockerClient.getContainer(containerId).isRunning());
    }

    private void testTagImage() throws DockerAccessException {
        dockerClient.tag(IMAGE, IMAGE_TAG,false);
        assertTrue(hasImage(IMAGE_TAG));

        dockerClient.removeImage(IMAGE_TAG, false);
        assertFalse(hasImage(IMAGE_TAG));
    }

    private boolean hasImage(String image) throws DockerAccessException {
        return dockerClient.hasImage(image);
    }
}
