package io.fabric8.maven.docker.access;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;



class DockerConnectionDetectorTest {

    DockerConnectionDetector detector = new DockerConnectionDetector(null);

    @Test
    void testGetUrlFromHostConfig() throws IOException {
        DockerConnectionDetector.ConnectionParameter param = detector.detectConnectionParameter("hostconfig", "certpath");
        Assertions.assertEquals("hostconfig", param.getUrl());
        Assertions.assertEquals("certpath", param.getCertPath());
    }

    @Test
    void testGetUrlFromEnvironment() throws MojoExecutionException, IOException {
        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost != null) {
            Assertions.assertEquals(dockerHost.replaceFirst("^tcp:/",""), detector.detectConnectionParameter(null, null).getUrl().replaceFirst("^https?:/", ""));
        } else if (System.getProperty("os.name").contains("Windows")) {
        	try {
                Assertions.assertEquals("npipe:////./pipe/docker_engine", detector.detectConnectionParameter(null, null).getUrl());
            } catch (IllegalArgumentException expectedIfNoUnixSocket) {
        	    // expected if no unix socket
            }
        } else {
            try {
                Assertions.assertEquals("unix:///var/run/docker.sock", detector.detectConnectionParameter(null, null).getUrl());
            } catch (IllegalArgumentException expectedIfNoUnixSocket) {
                // expected if no unix socket
            }
        }
    }

    @Test
    void testOrderDefaultDockerHostProviders() {
        Class[] expectedProviders = new Class[] {
            DockerConnectionDetector.EnvDockerHostProvider.class,
            DockerConnectionDetector.UnixSocketDockerHostProvider.class,
            DockerConnectionDetector.WindowsPipeDockerHostProvider.class
        };

        int i = 0;
        for (DockerConnectionDetector.DockerHostProvider provider : detector.dockerHostProviders) {
            Assertions.assertEquals(expectedProviders[i++], provider.getClass());
        }
    }

    @Test
    void testGetCertPathFromEnvironment() throws MojoExecutionException, IOException {
        try {
            DockerConnectionDetector.ConnectionParameter param = detector.detectConnectionParameter(null, null);
            String certPath = System.getenv("DOCKER_CERT_PATH");
            if (certPath != null) {
                Assertions.assertEquals(certPath, param.getCertPath());
            } else {
                String maybeUserDocker = param.getCertPath();
                if (maybeUserDocker != null) {
                    Assertions.assertEquals(new File(System.getProperty("user.home"), ".docker").getAbsolutePath(),
                                 maybeUserDocker);
                }
            }
        } catch (IllegalArgumentException exp) {
            // Can happen if there is now docker connection configured in the environment
        }
    }

    // any further testing requires a 'docker-machine' on the build host ...
}
