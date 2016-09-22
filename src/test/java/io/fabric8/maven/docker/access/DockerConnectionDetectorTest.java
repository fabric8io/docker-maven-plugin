package io.fabric8.maven.docker.access;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.PriorityQueue;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DockerConnectionDetectorTest {

    DockerConnectionDetector detector = new DockerConnectionDetector(null);

    @Test
    public void testGetUrlFromHostConfig() throws MojoExecutionException, IOException {
        assertEquals("hostconfig", detector.extractUrl("hostconfig"));
    }

    @Test
    public void testGetUrlFromEnvironment() throws MojoExecutionException, IOException {
        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost != null) {
            assertEquals(dockerHost.replaceFirst("^tcp:/",""), detector.extractUrl(null).replaceFirst("^https?:/", ""));
        } else if (System.getProperty("os.name").equalsIgnoreCase("Windows 10")) {
        	try {
                assertEquals("npipe:////./pipe/docker_engine", detector.extractUrl(null));
            } catch (IllegalArgumentException expectedIfNoUnixSocket) {
            }
        } else {
            try {
                assertEquals("unix:///var/run/docker.sock", detector.extractUrl(null));
            } catch (IllegalArgumentException expectedIfNoUnixSocket) {
            }
        }
    }

    @Test
    public void testOrderDefaultDockerHostProviders() {
        Class[] expectedProviders = new Class[] {
            DockerConnectionDetector.EnvDockerHostProvider.class,
            DockerConnectionDetector.UnixSocketDockerHostProvider.class,
            DockerConnectionDetector.WindowsPipeDockerHostProvider.class
        };

        int i = 0;
        for (DockerConnectionDetector.DockerHostProvider provider : detector.dockerHostProviders) {
            assertEquals(expectedProviders[i++], provider.getClass());
        }
    }

    @Test
    public void testGetCertPathFromCertConfig() throws MojoExecutionException, IOException {
        assertEquals("certconfig", detector.getCertPath("certconfig"));
    }

    @Test
    public void testGetCertPathFromEnvironment() throws MojoExecutionException, IOException {
        String certPath = System.getenv("DOCKER_CERT_PATH");
        if (certPath != null) {
            assertEquals(certPath, detector.getCertPath(null));
        } else {
            String maybeUserDocker = detector.getCertPath(null);
            if (maybeUserDocker != null) {
                assertEquals(new File(System.getProperty("user.home"), ".docker").getAbsolutePath(),
                        maybeUserDocker);
            }
        }
    }

    // any further testing requires a 'docker-machine' on the build host
}
