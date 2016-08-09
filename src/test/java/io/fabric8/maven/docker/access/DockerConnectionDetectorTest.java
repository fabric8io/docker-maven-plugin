package io.fabric8.maven.docker.access;

import java.io.File;
import java.io.IOException;

import io.fabric8.maven.docker.access.DockerConnectionDetector;
import io.fabric8.maven.docker.util.AnsiLogger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Assert;
import org.junit.Test;

public class DockerConnectionDetectorTest {

    AnsiLogger logger = new AnsiLogger(new SystemStreamLog(), true, true);
    DockerConnectionDetector machine = new DockerConnectionDetector(logger, null);

    @Test
    public void testGetUrlFromHostConfig() throws MojoExecutionException, IOException {
        Assert.assertEquals("hostconfig", machine.extractUrl("hostconfig"));
    }

    @Test
    public void testGetUrlFromEnvironment() throws MojoExecutionException, IOException {
        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost != null) {
            Assert.assertEquals(dockerHost.replaceFirst("^tcp:/",""), machine.extractUrl(null).replaceFirst("^https?:/",""));
        } else if (System.getProperty("os.name").equalsIgnoreCase("Windows 10")) {
        	try {
                Assert.assertEquals("npipe:////./pipe/docker_engine", machine.extractUrl(null));
            } catch (IllegalArgumentException expectedIfNoUnixSocket) {
            }
        } else {
            try {
                Assert.assertEquals("unix:///var/run/docker.sock", machine.extractUrl(null));
            } catch (IllegalArgumentException expectedIfNoUnixSocket) {
            }
        }
    }

    @Test
    public void testGetCertPathFromCertConfig() throws MojoExecutionException, IOException {
        Assert.assertEquals("certconfig", machine.getCertPath("certconfig"));
    }

    @Test
    public void testGetCertPathFromEnvironment() throws MojoExecutionException, IOException {
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
