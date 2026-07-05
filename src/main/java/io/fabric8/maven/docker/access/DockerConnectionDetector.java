package io.fabric8.maven.docker.access;

import java.io.*;
import java.util.*;

import io.fabric8.maven.docker.access.hc.wslc.WslcClientBuilder;
import io.fabric8.maven.docker.access.util.LocalSocketUtil;
import io.fabric8.maven.docker.util.*;

import javax.annotation.Nonnull;

import static io.fabric8.maven.docker.util.EnvUtil.getUserHome;

/**
 * Detector for determining the Docker access mechanism
 */
public class DockerConnectionDetector {

    final List<DockerHostProvider> dockerHostProviders;

    public DockerConnectionDetector(List<DockerHostProvider> externalProviders) {
        dockerHostProviders = new ArrayList<>();
        dockerHostProviders.addAll(getDefaultEnvProviders());
        if (externalProviders != null) {
            dockerHostProviders.addAll(externalProviders);
        }
        Collections.sort(dockerHostProviders,new DockerHostProvider.Comparator());
    }

    private Collection<? extends DockerHostProvider> getDefaultEnvProviders() {
        return Arrays.asList(new EnvDockerHostProvider(),
                             new UnixSocketDockerHostProvider(),
                             new WindowsPipeDockerHostProvider(),
                             new WslcDockerHostProvider());
    }


    /**
     * Provider of environment variables like 'DOCKER_HOST'
     */
    public interface DockerHostProvider {
        /**
         * Get value of the docker host as detected by this provider. Return null if it couldn't be detected.
         *
         * @return the docker host parameter or null
         * @param certPath
         */
        ConnectionParameter getConnectionParameter(String certPath) throws IOException;

        /**
         * Get the priority of the env provider. A priority of -1 means, this is a 'fallback' called
         * as last resort.
         *
         * @return priority, the higher, the earlier the provider is called.
         * The highest priority of internal providers are not larger than 100.
         */
        int getPriority();

        class Comparator implements java.util.Comparator<DockerHostProvider> {
            @Override
            public int compare(DockerHostProvider o1, DockerHostProvider o2) {
                return o2.getPriority() - o1.getPriority();
            }
        }
    }

    /**
     * Get the docker host url.
     * <ol>
     *   <li>From &lt;dockerHost&gt; configuration</li>
     *   <li>From &lt;machine&gt; configuration</li>
     *   <li>From DOCKER_HOST environment variable</li>
     *   <li>Default to /var/run/docker.sock</li>
     * </ol>
     * @param dockerHost The dockerHost configuration setting
     * @return The docker host url
     * @throws IOException when URL handling fails
     */
    public ConnectionParameter detectConnectionParameter(String dockerHost, String certPath) throws IOException {
        if (dockerHost != null) {
            return new ConnectionParameter(dockerHost, certPath);
        }
        for (DockerHostProvider provider : dockerHostProviders) {
            ConnectionParameter value = provider.getConnectionParameter(certPath);
            if (value != null) {
                return value;
            }
        }
        throw new IllegalArgumentException("No <dockerHost> given, no DOCKER_HOST environment variable, " +
                                           "no read/writable '/var/run/docker.sock' or '//./pipe/docker_engine', " +
                                           "no running 'wslc' (WSL Containers) session " +
                                           "and no external provider like Docker machine configured");
    }

    // ====================================================================================================

    // Lookup from the enviroment
    class EnvDockerHostProvider implements DockerHostProvider {
        @Override
        public ConnectionParameter getConnectionParameter(String certPath) throws IOException {
            String connect = System.getenv("DOCKER_HOST");
            return connect != null ? new ConnectionParameter(connect, certPath) : null;
        }

        @Override
        public int getPriority() {
            return 100;
        }
    }

    // Check for a unix socket
    class UnixSocketDockerHostProvider implements DockerHostProvider {
        @Override
        public ConnectionParameter getConnectionParameter(String certPath) throws IOException {
            File unixSocket = new File("/var/run/docker.sock");
            if (unixSocket.exists() && unixSocket.canRead() && unixSocket.canWrite() && LocalSocketUtil.canConnectUnixSocket(unixSocket)) {
                return new ConnectionParameter("unix:///var/run/docker.sock", certPath);
            } else {
                return null;
            }
        }

        @Override
        public int getPriority() {
            return 55;
        }
    }

    // Check for a windows pipe
    class WindowsPipeDockerHostProvider implements DockerHostProvider {
        @Override
        public ConnectionParameter getConnectionParameter(String certPath) throws IOException {
            File windowsPipe = new File("//./pipe/docker_engine");
            if (windowsPipe.exists()) {
                return new ConnectionParameter("npipe:////./pipe/docker_engine", certPath);
            } else {
                return null;
            }
        }

        @Override
        public int getPriority() {
            return 50;
        }
    }

    // Check for WSL Containers (wslc). The Docker daemon runs inside the wslc VM and is not
    // exposed as a Windows named pipe or TCP port, so it is reached through a stdio bridge
    // (see the 'wslc://' transport). Priority is below the real Docker/Podman named pipe so an
    // existing Docker Desktop / Podman endpoint always wins; wslc is only used as a fallback.
    static class WslcDockerHostProvider implements DockerHostProvider {

        // Allow overriding the wslc executable (e.g. non-default install location) when probing for
        // availability. The transport resolves the same WSLC_EXECUTABLE variable, so it is not
        // carried through the connection URL (which would break on paths with spaces or backslashes).
        private static final String WSLC_EXECUTABLE =
            System.getenv("WSLC_EXECUTABLE") != null ? System.getenv("WSLC_EXECUTABLE") : "wslc.exe";

        @Override
        public ConnectionParameter getConnectionParameter(String certPath) throws IOException {
            if (!isWindows() || !isWslcAvailable()) {
                return null;
            }
            return new ConnectionParameter(WslcClientBuilder.AUTO_DETECT_URL, certPath);
        }

        @Override
        public int getPriority() {
            return 45;
        }

        private boolean isWindows() {
            return System.getProperty("os.name", "").toLowerCase().contains("win");
        }

        // 'wslc version' is a cheap metadata call that does not start the container VM.
        boolean isWslcAvailable() {
            Process process = null;
            try {
                process = startWslcVersionProbe();
                if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return false;
                }
                return process.exitValue() == 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (IOException | RuntimeException e) {
                return false;
            } finally {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        }

        // Spawn the 'wslc version' probe. Package-visible and overridable so a test can inject a
        // controlled process (this is a Windows-only path, otherwise never run on the Linux CI).
        Process startWslcVersionProbe() throws IOException {
            // Windows-only method (guarded by isWindows() in the caller), so discard output to NUL.
            return new ProcessBuilder(WSLC_EXECUTABLE, "version")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(new File("NUL")))
                .start();
        }
    }

    public static class ConnectionParameter {
        private final String url;
        private String certPath;

        public ConnectionParameter(@Nonnull String url, String certPath) throws IOException {
            this.url = EnvUtil.convertTcpToHttpUrl(url);
            initCertPath(certPath);
        }

        public String getUrl() {
            return url;
        }

        public String getCertPath() {
            return certPath;
        }

        /**
         * Get the docker certificate location
         * <ol>
         *   <li>From &lt;certPath&gt; a given cert config argument</li>
         *   <li>From DOCKER_CERT_PATH environment variable</li>
         *   <li>Default to ${user.home}/.docker</li>
         * </ol>
         * @param certPath the configured certification path which is used directly if set
         */
        private void initCertPath(String certPath) throws IOException {
            this.certPath = certPath != null ? certPath : System.getenv("DOCKER_CERT_PATH");
            // Try default locations as last resort
            if (this.certPath == null) {
                File dockerHome = new File(getUserHome() + "/.docker");
                if (dockerHome.isDirectory()) {
                    String[] entries = dockerHome.list(SuffixFileFilter.PEM_FILTER);
                    if (entries == null) {
                        throw new IOException("Can not read directory " + dockerHome + ". Please check file permissions.");
                    }
                    if (entries.length > 0) {
                        this.certPath = dockerHome.getAbsolutePath();
                    }
                }
            }
        }
    }
}
