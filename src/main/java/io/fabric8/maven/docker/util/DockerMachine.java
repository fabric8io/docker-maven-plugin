package io.fabric8.maven.docker.util;

import java.io.File;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;

import io.fabric8.maven.docker.AbstractDockerMojo;
import io.fabric8.maven.docker.config.MachineConfiguration;

/**
 * get environment from DockerMachine
 */
public class DockerMachine {

    private final Logger log;
    private final MachineConfiguration machine;

    public DockerMachine(Logger log, MachineConfiguration machine) {
        this.log = log;
        this.machine = machine;
    }

    /**
     * Get the docker host url.
     * <ol>
     * <li>From &lt;dockerHost&gt; configuration</li>
     * <li>From &lt;machine&gt; configuration</li>
     * <li>From DOCKER_HOST environment variable</li>
     * <li>Default to /var/run/docker.sock</li>
     * </ol>
     * @param dockerHost The dockerHost configuration setting
     * @return The docker host url
     * @throws MojoExecutionException
     */
    public String extractUrl(String dockerHost) throws MojoExecutionException {
        String connect = getUrl("DOCKER_HOST", dockerHost);
        if (connect != null) {
            String protocol = connect.contains(":" + AbstractDockerMojo.DOCKER_HTTPS_PORT) ? "https:" : "http:";
            return connect.replaceFirst("^tcp:", protocol);
        }
        File unixSocket = new File("/var/run/docker.sock");
        if (unixSocket.exists() && unixSocket.canRead() && unixSocket.canWrite()) {
            return "unix:///var/run/docker.sock";
        } 
        throw new IllegalArgumentException("No <dockerHost> or <machine> given, no DOCKER_HOST environment variable, and no read/writable '/var/run/docker.sock'");
    }
    
    /**
     * Get the docker certificate location
     * <ol>
     * <li>From &lt;certPath&gt; configuration</li>
     * <li>From &lt;machine&gt; configuration</li>
     * <li>From DOCKER_CERT_PATH environment variable</li>
     * <li>Default to ${user.home}/.docker</li>
     * </ol>
     * @param dockerHost The dockerHost configuration setting
     * @return The docker certificate location, or null
     * @throws MojoExecutionException
     */
    public String getCertPath(String certPath) throws MojoExecutionException {
        String path = getUrl("DOCKER_CERT_PATH", certPath);
        if (path == null) {
            File dockerHome = new File(System.getProperty("user.home") + "/.docker");
            if (dockerHome.isDirectory() && dockerHome.list(SuffixFileFilter.PEM_FILTER).length > 0) {
                return dockerHome.getAbsolutePath();
            }
        }
        return path;
    }
    
    /**
     * Get the option from the key
     */
    private String getUrl(String key, String value) throws MojoExecutionException {
        if (value != null) {
            return value;
        }
        value = getDockerMachineEnv(key);
        if (value != null) {
            return value;
        }
        return System.getenv(key);
    }


    /**
     * Get a docker configuration value from the <machine> configuration section
     * 
     * @param key The configuration name
     * @return The configuration value
     * @throws MojoExecutionException
     */
    public String getDockerMachineEnv(String key) throws MojoExecutionException {
		String value = System.getenv(key);
		if (value != null) {
			return value;
		}
		if (machine != null) {
			Map<String, String> env = machine.getEnv();
			if (env == null) {
				env = new DockerEnvironment(log, machine).getEnvironment();
				machine.setEnv(env);
			}
			return env.get(key);
		}
		return null;
    }
}
