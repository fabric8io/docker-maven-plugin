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
                String url = detector.detectConnectionParameter(null, null).getUrl();
                // A real Docker/Podman named pipe wins if present; otherwise wslc is detected as a fallback.
                Assertions.assertTrue("npipe:////./pipe/docker_engine".equals(url) || url.startsWith("wslc://"),
                    "Unexpected detected docker host on Windows: " + url);
            } catch (IllegalArgumentException expectedIfNoUnixSocket) {
        	    // expected if neither a named pipe nor wslc is available
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
            DockerConnectionDetector.WindowsPipeDockerHostProvider.class,
            DockerConnectionDetector.WslcDockerHostProvider.class
        };

        int i = 0;
        for (DockerConnectionDetector.DockerHostProvider provider : detector.dockerHostProviders) {
            Assertions.assertEquals(expectedProviders[i++], provider.getClass());
        }
    }

    @Test
    void testWslcProviderPriorityAndNonWindowsDetection() throws IOException {
        DockerConnectionDetector.WslcDockerHostProvider provider = new DockerConnectionDetector.WslcDockerHostProvider();
        // Below the docker_engine named pipe (50) so a real Docker/Podman endpoint always wins.
        Assertions.assertEquals(45, provider.getPriority());
        // On non-Windows the provider is inert (never probes for wslc).
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            Assertions.assertNull(provider.getConnectionParameter(null));
        }
    }

    @Test
    void testWslcAvailabilityProbeBranches() {
        // 'wslc version' exiting 0 -> available.
        Assertions.assertTrue(providerWithProbe(fakeProcess(true, 0, false)).isWslcAvailable());
        // Non-zero exit -> not available.
        Assertions.assertFalse(providerWithProbe(fakeProcess(true, 3, false)).isWslcAvailable());
        // Probe does not finish within the timeout -> not available (and gets force-killed).
        Assertions.assertFalse(providerWithProbe(fakeProcess(false, 0, true)).isWslcAvailable());
        // Spawning the probe fails (wslc not installed) -> not available.
        Assertions.assertFalse(providerWithProbe(null).isWslcAvailable());
    }

    @Test
    void testStartWslcVersionProbeRuns() {
        // Exercise the real probe launcher. wslc is absent on the CI host, so starting it throws
        // IOException; on a dev box with wslc installed it returns a live process we then kill.
        DockerConnectionDetector.WslcDockerHostProvider provider = new DockerConnectionDetector.WslcDockerHostProvider();
        Process probe = null;
        boolean launchFailed = false;
        try {
            probe = provider.startWslcVersionProbe();
        } catch (IOException expectedWhenWslcAbsent) {
            // wslc executable not on PATH: the ProcessBuilder ran and start() failed, as intended.
            launchFailed = true;
        } finally {
            if (probe != null) {
                probe.destroyForcibly();
            }
            // The probe discards output to "NUL"; on non-Windows that is a real file, so clean it up.
            java.io.File nul = new java.io.File("NUL");
            if (nul.isFile()) {
                nul.delete();
            }
        }
        // Either wslc launched and returned a process, or it was absent and starting it failed with
        // an IOException. Any other failure mode would already have thrown out of the block above.
        Assertions.assertTrue(probe != null || launchFailed);
    }

    private static DockerConnectionDetector.WslcDockerHostProvider providerWithProbe(Process probe) {
        return new DockerConnectionDetector.WslcDockerHostProvider() {
            @Override
            Process startWslcVersionProbe() throws IOException {
                if (probe == null) {
                    throw new IOException("wslc not installed");
                }
                return probe;
            }
        };
    }

    /** A canned {@link Process} for exercising the availability probe without spawning anything. */
    private static Process fakeProcess(boolean finishesInTime, int exitValue, boolean aliveAfterTimeout) {
        return new Process() {
            @Override public java.io.OutputStream getOutputStream() { return null; }
            @Override public java.io.InputStream getInputStream() { return null; }
            @Override public java.io.InputStream getErrorStream() { return null; }
            @Override public int waitFor() { return exitValue; }
            @Override public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) { return finishesInTime; }
            @Override public int exitValue() { return exitValue; }
            @Override public boolean isAlive() { return aliveAfterTimeout; }
            @Override public void destroy() { /* no-op stub */ }
            @Override public Process destroyForcibly() { return this; }
        };
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
