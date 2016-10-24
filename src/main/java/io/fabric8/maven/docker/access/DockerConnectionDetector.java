package io.fabric8.maven.docker.access;

import java.io.*;
import java.util.*;

import io.fabric8.maven.docker.access.util.LocalSocketUtil;
import io.fabric8.maven.docker.util.*;

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
                             new WindowsPipeDockerHostProvider());
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
                                           "no read/writable '/var/run/docker.sock' or '//./pipe/docker_engine' " +
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

    public static class ConnectionParameter {
        private final String url;
        private String certPath;

        public ConnectionParameter(String url, String certPath) throws IOException {
            this.url = url != null ? EnvUtil.convertTcpToHttpUrl(url) : null;
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
                File dockerHome = new File(System.getProperty("user.home") + "/.docker");
                if (dockerHome.isDirectory() && dockerHome.list(SuffixFileFilter.PEM_FILTER).length > 0) {
                    this.certPath = dockerHome.getAbsolutePath();
                }
            }
        }
    }
}
