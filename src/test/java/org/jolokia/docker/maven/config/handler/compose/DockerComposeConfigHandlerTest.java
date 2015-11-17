package org.jolokia.docker.maven.config.handler.compose;

import static org.junit.Assert.*;

import java.util.List;

import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.config.RunImageConfiguration;
import org.jolokia.docker.maven.config.VolumeConfiguration;
import org.jolokia.docker.maven.config.external.DockerComposeConfiguration;
import org.jolokia.docker.maven.config.external.ExternalImageConfiguration;
import org.junit.Before;
import org.junit.Test;

public class DockerComposeConfigHandlerTest {

    private DockerComposeConfigHandler handler;

    private List<ImageConfiguration> resolved;

    private ImageConfiguration unresolved;

    @Before
    public void setup() {
        this.handler = new DockerComposeConfigHandler();
    }

    @Test
    public void testFullResolve() {
        givenAnUnresolvedImage();
        whenResolveImages();
        thenResolveImageSizeIs(1);
        thenResolvedImageIsCorrect();
    }

    private void givenAnUnresolvedImage() {
        DockerComposeConfiguration composeConfig = new DockerComposeConfiguration.Builder()
                .dockerComposeDir(getClass().getResource("/compose").getFile())
                .build();

        ExternalImageConfiguration externalConfig = new ExternalImageConfiguration.Builder()
                .compose(composeConfig)
                .build();

        unresolved = new ImageConfiguration.Builder()
                .externalConfig(externalConfig)
                .build();
    }

    private void thenListContains(List<String> list, String value) {
        assertNotNull(list);
        assertFalse(list.isEmpty());
        assertTrue(list.contains(value));
    }
    
    private void thenFullyResolvedRunImageIsCorrect(RunImageConfiguration config) {
        assertEquals("command", config.getCmd().getShell());
        assertEquals("domainname", config.getDomainname());
        assertEquals("entrypoint.sh", config.getEntrypoint().getShell());
        assertEquals("hostname", config.getHostname());
        assertEquals(1000L, config.getMemory().longValue());
        assertEquals(1001L, config.getMemorySwap().longValue());
        assertEquals(true, config.getPrivileged().booleanValue());
        assertEquals("user", config.getUser());
        assertEquals("/workingDir", config.getWorkingDir());
        
        // TODO: implement this
        assertNull(config.getEnvPropertyFile());
        
        thenListContains(config.getCapAdd(), "ADD");
        thenListContains(config.getCapDrop(), "DROP");
        thenListContains(config.getDns(), "8.8.8.8");
        thenListContains(config.getDnsSearch(), "example.com");
        thenListContains(config.getExtraHosts(), "host:1.2.3.4");
        
        assertEquals(3, config.getEnv().size());
        
        VolumeConfiguration volumesConfig = config.getVolumeConfiguration();
        assertNotNull(volumesConfig);
        assertEquals(1, volumesConfig.getFrom().size());
    }
    
    private void thenResolvedImageIsCorrect() {
        ImageConfiguration config = resolved.get(0);

        assertEquals("image", config.getName());
        assertEquals("service", config.getAlias());

        thenFullyResolvedRunImageIsCorrect(config.getRunConfiguration());
    }

    private void thenResolveImageSizeIs(int size) {
        assertEquals(size, resolved.size());
    }

    private void whenResolveImages() {
        resolved = handler.resolve(unresolved, null);
    }
}
