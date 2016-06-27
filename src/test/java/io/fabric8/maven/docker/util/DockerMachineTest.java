package io.fabric8.maven.docker.util;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Assert;
import org.junit.Test;

public class DockerMachineTest {

    AnsiLogger logger = new AnsiLogger(new SystemStreamLog(), true, true);
    DockerMachine machine = new DockerMachine(logger, null);

    @Test
    public void testGetUrlFromHostConfig() throws MojoExecutionException {
        Assert.assertEquals("hostconfig", machine.extractUrl("hostconfig"));
    }

    @Test
    public void testGetUrlFromEnvironment() throws MojoExecutionException {
        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost != null) {
            Assert.assertEquals(dockerHost, machine.extractUrl(null));
        } else {
            try {
                Assert.assertEquals("/var/run/docker.sock", machine.extractUrl(null));
            } catch (IllegalArgumentException expectedIfNoUnixSocket) {
            }
        }
    }

    @Test
    public void testGetCertPathFromCertConfig() throws MojoExecutionException {
        Assert.assertEquals("certconfig", machine.getCertPath("certconfig"));
    }

    @Test
    public void testGetCertPathFromEnvironment() throws MojoExecutionException {
        String certPath = System.getenv("DOCKER_CERT_PATH");
        if (certPath != null) {
            Assert.assertEquals(certPath, machine.getCertPath(null));
        } else {
            String maybeUserDocker = machine.getCertPath(null);
            if (maybeUserDocker != null) {
                Assert.assertEquals(new File(System.getProperty("user.home"), ".docker").getAbsolutePath(),
                        maybeUserDocker);
            }
        }
    }

    // any further testing requires a 'docker-machine' on the build host
}
