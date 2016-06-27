package io.fabric8.maven.docker.access;

import java.io.File;
import java.util.Map;

import io.fabric8.maven.docker.util.*;
import org.apache.maven.plugin.MojoExecutionException;

import io.fabric8.maven.docker.config.DockerMachineConfiguration;

/**
 * Detector for determining the Docker access mechanism
 */
public class DockerConnectionDetector {

    private final Logger log;
    private final DockerMachineConfiguration machineConfig;

    // Environment from a docker machine
    private Map<String, String> dockerMachineEnv;
    private DockerMachine dockerMachine;

    public DockerConnectionDetector(Logger log, DockerMachineConfiguration machineConfig) {
        this.log = log;
        this.machineConfig = machineConfig;
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
     * @throws MojoExecutionException
     */
    public String extractUrl(String dockerHost) throws MojoExecutionException {
        String connect = getValueWithFallback(dockerHost, "DOCKER_HOST");
        if (connect != null) {
            String protocol = connect.contains(":" + EnvUtil.DOCKER_HTTPS_PORT) ? "https:" : "http:";
            return connect.replaceFirst("^tcp:", protocol);
        }
        File unixSocket = new File("/var/run/docker.sock");
        if (unixSocket.exists() && unixSocket.canRead() && unixSocket.canWrite()) {
            return "unix:///var/run/docker.sock";
        } 
        throw new MojoExecutionException("No <dockerHost> or <machine> given, no DOCKER_HOST environment variable, and no read/writable '/var/run/docker.sock'");
    }
    
    /**
     * Get the docker certificate location
     * <ol>
     *   <li>From &lt;certPath&gt; configuration</li>
     *   <li>From &lt;machine&gt; configuration</li>
     *   <li>From DOCKER_CERT_PATH environment variable</li>
     *   <li>Default to ${user.home}/.docker</li>
     * </ol>
     * @param certPath the configured certification path
     * @return The docker certificate location, or null
     * @throws MojoExecutionException
     */
    public String getCertPath(String certPath) throws MojoExecutionException {
        String path = getValueWithFallback(certPath, "DOCKER_CERT_PATH");
        // Final fallback
        if (path == null) {
            File dockerHome = new File(System.getProperty("user.home") + "/.docker");
            if (dockerHome.isDirectory() && dockerHome.list(SuffixFileFilter.PEM_FILTER).length > 0) {
                return dockerHome.getAbsolutePath();
            }
        }
        return path;
    }

    // ====================================================================================================

    /**
     * Use the given value if non-null or lookup from environment. First the
     * system environment is checked, then the environment from a docker machine (if given)
     *
     * A docker machine can be automatically started / created lazily if the configuration given
     * in the constructo allows this.
     *
     * @param value to check
     * @param envVar the env var to use as fallback
     */
    private String getValueWithFallback(String value, String envVar) throws MojoExecutionException {
        if (value != null) {
            return value;
        }
        value = System.getenv(envVar);
        if (value != null) {
			return value;
		}
        if (machineConfig != null) {
            // Startup docker machine
            if (dockerMachine == null) {
                dockerMachine = new DockerMachine(log, machineConfig);
            }
            // Get env and cache
            if (dockerMachineEnv == null) {
                dockerMachineEnv = dockerMachine.getEnvironment();
            }
            return dockerMachineEnv.get(envVar);
        } else {
            return null;
        }
    }


}
