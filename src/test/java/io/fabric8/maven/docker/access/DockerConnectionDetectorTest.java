package io.fabric8.maven.docker.access;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Assert;
import org.junit.Test;

public class DockerConnectionDetectorTest {

    DockerConnectionDetector detector = new DockerConnectionDetector(null);

    @Test
    public void testGetUrlFromHostConfig() throws MojoExecutionException, IOException {
        Assert.assertEquals("hostconfig", detector.extractUrl("hostconfig"));
    }

    @Test
    public void testGetUrlFromEnvironment() throws MojoExecutionException, IOException {
        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost != null) {
            Assert.assertEquals(dockerHost.replaceFirst("^tcp:/",""), detector.extractUrl(null).replaceFirst("^https?:/", ""));
        } else if (System.getProperty("os.name").equalsIgnoreCase("Windows 10")) {
        	try {
                Assert.assertEquals("npipe:////./pipe/docker_engine", detector.extractUrl(null));
            } catch (IllegalArgumentException expectedIfNoUnixSocket) {
            }
        } else {
            try {
                Assert.assertEquals("unix:///var/run/docker.sock", detector.extractUrl(null));
            } catch (IllegalArgumentException expectedIfNoUnixSocket) {
            }
        }
    }

    @Test
    public void testGetCertPathFromCertConfig() throws MojoExecutionException, IOException {
        Assert.assertEquals("certconfig", detector.getCertPath("certconfig"));
    }

    @Test
    public void testGetCertPathFromEnvironment() throws MojoExecutionException, IOException {
        String certPath = System.getenv("DOCKER_CERT_PATH");
        if (certPath != null) {
            Assert.assertEquals(certPath, detector.getCertPath(null));
        } else {
            String maybeUserDocker = detector.getCertPath(null);
            if (maybeUserDocker != null) {
                Assert.assertEquals(new File(System.getProperty("user.home"), ".docker").getAbsolutePath(),
                        maybeUserDocker);
            }
        }
    }

    // any further testing requires a 'docker-machine' on the build host
}
