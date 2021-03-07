package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.Logger;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

public class VolumeRemoveMojoTest {

    @Injectable
    Logger log;

    @Tested(fullyInitialized = false)
    private VolumeRemoveMojo volumeRemoveMojo;

    @Mocked
    ServiceHub serviceHub;

    @Test
    public void removeVolumeGetVolumesReturnsNull() throws DockerAccessException, MojoExecutionException {
        volumeRemoveMojo.executeInternal(serviceHub);
        new Verifications(){{
            log.info("No volume configuration found."); times = 1;
        }};
    }
}
