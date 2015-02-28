package integration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import edu.emory.mathcs.backport.java.util.Arrays;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.jolokia.docker.maven.AbstractDockerMojo;
import org.jolokia.docker.maven.access.*;
import org.jolokia.docker.maven.util.AnsiLogger;
import org.jolokia.docker.maven.util.EnvUtil;
import org.junit.*;

import static org.junit.Assert.*;

/*
 * if run from your ide, this test assumes you have configured the runner w/ the appropriate env variables
 * 
 * it also assumes that 'removeImage' does what it's supposed to do as it's used in test setup.
 */
@Ignore
public class DockerClientIT {

    private static final String IMAGE = "busybox:buildroot-2014.02";
    
    private static final String IMAGE_TAG = "busybox:tagged";
    private static final int PORT = 5677;

    private String containerId;
    private final DockerAccessWithHttpClient dockerClient; 
    
    public DockerClientIT() {
        this.dockerClient = createClient(EnvUtil.extractUrl(null), new AnsiLogger(new SystemStreamLog(), true));
    }

    @Before
    public void setup() throws DockerAccessException {
        // yes - this is a big don't do for tests
        testRemoveImage(IMAGE);
        testRemoveImage(IMAGE_TAG);
    }
    
    @Test
    public void testBuildImage() throws DockerAccessException {
        File file = new File("src/test/resources/integration/busybox-test.tar");
        dockerClient.buildImage(IMAGE_TAG, file);        
        assertTrue(dockerClient.hasImage(IMAGE_TAG));
        
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
            testQueryPortMapping();
            testStopContainer();
        } finally {
            testRemoveImage(IMAGE);
        }
    }
 
    private DockerAccessWithHttpClient createClient(String baseUrl, AnsiLogger logger) {
        try {
            return new DockerAccessWithHttpClient(AbstractDockerMojo.API_VERSION, baseUrl, null, logger);
        } catch (IOException e) {
            // not using ssl, so not going to happen
            throw new RuntimeException();
        }
    }

    private void testCreateContainer() throws DockerAccessException {
        PortMapping portMapping = new PortMapping(Arrays.asList(new Object[] { PORT + ":" + PORT }), new Properties());
        ContainerHostConfig hostConfig = new ContainerHostConfig().portBindings(portMapping);
        ContainerCreateConfig createConfig = new ContainerCreateConfig(IMAGE).command("ping google.com").hostConfig(hostConfig);
        
        containerId = dockerClient.createContainer(createConfig);
        assertNotNull(containerId);

        // TODO: enhance this to check/set container name when issue 48 is resolved
        String name = dockerClient.getContainerName(containerId);
        assertNotNull(name);       
    }
    
    private void testDoesNotHave() throws DockerAccessException {
        assertFalse(dockerClient.hasImage(IMAGE));
    }
    
    private void testPullImage() throws DockerAccessException {
        dockerClient.pullImage(IMAGE, null, null);
        assertTrue(dockerClient.hasImage(IMAGE));
    }
    
    private void testQueryPortMapping() throws DockerAccessException {
        Map<String, Integer> portMap = dockerClient.queryContainerPortMapping(containerId);
        assertTrue(portMap.containsValue(5677));
    }
    
    private void testRemoveImage(String image) throws DockerAccessException {
        dockerClient.removeImage(image, false);
        assertFalse(dockerClient.hasImage(image));
    }
     
    private void testStartContainer() throws DockerAccessException {
        dockerClient.startContainer(containerId);
        assertTrue(dockerClient.isContainerRunning(containerId));
    }
    
    private void testStopContainer() throws DockerAccessException {
        dockerClient.stopContainer(containerId);
        assertFalse(dockerClient.isContainerRunning(containerId));
    }
    
    private void testTagImage() throws DockerAccessException {
        dockerClient.tag(IMAGE, IMAGE_TAG,false);
        assertTrue(dockerClient.hasImage(IMAGE_TAG));
        
        dockerClient.removeImage(IMAGE_TAG, false);
        assertFalse(dockerClient.hasImage(IMAGE_TAG));
    }
}
