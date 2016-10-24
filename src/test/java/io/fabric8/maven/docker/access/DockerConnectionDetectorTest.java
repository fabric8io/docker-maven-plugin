package io.fabric8.maven.docker.access;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DockerConnectionDetectorTest {

    DockerConnectionDetector detector = new DockerConnectionDetector(null);

    @Test
    public void testGetUrlFromHostConfig() throws MojoExecutionException, IOException {
        DockerConnectionDetector.ConnectionParameter param = detector.detectConnectionParameter("hostconfig", "certpath");
        assertEquals("hostconfig", param.getUrl());
        assertEquals("certpath", param.getCertPath());
    }

    @Test
    public void testGetUrlFromEnvironment() throws MojoExecutionException, IOException {
        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost != null) {
            assertEquals(dockerHost.replaceFirst("^tcp:/",""), detector.detectConnectionParameter(null, null).getUrl().replaceFirst("^https?:/", ""));
        } else if (System.getProperty("os.name").equalsIgnoreCase("Windows 10")) {
        	try {
                assertEquals("npipe:////./pipe/docker_engine", detector.detectConnectionParameter(null, null).getUrl());
            } catch (IllegalArgumentException expectedIfNoUnixSocket) {
            }
        } else {
            try {
                assertEquals("unix:///var/run/docker.sock", detector.detectConnectionParameter(null, null).getUrl());
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
    public void testGetCertPathFromEnvironment() throws MojoExecutionException, IOException {
        try {
            DockerConnectionDetector.ConnectionParameter param = detector.detectConnectionParameter(null, null);
            String certPath = System.getenv("DOCKER_CERT_PATH");
            if (certPath != null) {
                assertEquals(certPath, param.getCertPath());
            } else {
                String maybeUserDocker = param.getCertPath();
                if (maybeUserDocker != null) {
                    assertEquals(new File(System.getProperty("user.home"), ".docker").getAbsolutePath(),
                                 maybeUserDocker);
                }
            }
        } catch (IllegalArgumentException exp) {
            // Can happen if there is now docker connection configured in the environment
        }
    }

    // any further testing requires a 'docker-machine' on the build host ...
}
