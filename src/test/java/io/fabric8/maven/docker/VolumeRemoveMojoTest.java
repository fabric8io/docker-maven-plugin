package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VolumeRemoveMojoTest {

    @Mock
    AnsiLogger log;

    @InjectMocks
    private VolumeRemoveMojo volumeRemoveMojo;

    @Mock
    ServiceHub serviceHub;

    @Test
    void removeVolumeGetVolumesReturnsNull() throws DockerAccessException, MojoExecutionException {
        volumeRemoveMojo.executeInternal(serviceHub);
        Mockito.verify(log).info("No volume configuration found.");
    }
}
