package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.AnsiLogger;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VolumeCreateMojoTest {

    @Mock
    AnsiLogger log;

    @InjectMocks
    private VolumeCreateMojo volumeCreateMojo;

    @Mock
    ServiceHub serviceHub;

    @Test
    void createVolumeGetVolumesReturnsNull() throws DockerAccessException, MojoExecutionException {
        volumeCreateMojo.executeInternal(serviceHub);
        Mockito.verify(log).info("No volume configuration found.");
    }
}
