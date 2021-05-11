package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.Logger;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

public class VolumeCreateMojoTest {

    @Injectable
    AnsiLogger log;

    @Tested(fullyInitialized = false)
    private VolumeCreateMojo volumeCreateMojo;

    @Mocked
    ServiceHub serviceHub;

    @Test
    public void createVolumeGetVolumesReturnsNull() throws DockerAccessException, MojoExecutionException {
        volumeCreateMojo.executeInternal(serviceHub);
        new Verifications(){{
            log.info("No volume configuration found."); times = 1;
        }};
    }
}
