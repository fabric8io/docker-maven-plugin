package io.fabric8.maven.docker.util;

import java.io.File;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;

import io.fabric8.maven.docker.AbstractDockerMojo;

/**
 * get environment from DockerMachine
 */
public class DockerMachine {

    private final Logger log;
    private final Map pluginContext;
    
    public DockerMachine(Logger log, Map pluginContext) {
        this.log = log;
        this.pluginContext = pluginContext;
    }

    // Check both, url and env DOCKER_HOST (first takes precedence)
    public String extractUrl(String dockerHost) throws MojoExecutionException {
        String connect = dockerHost != null ? dockerHost : getDockerMachineEnv("DOCKER_HOST");
        if (connect == null) {
            File unixSocket = new File("/var/run/docker.sock");
            if (unixSocket.exists() && unixSocket.canRead() && unixSocket.canWrite()) {
                connect = "unix:///var/run/docker.sock";
            } else {
                throw new IllegalArgumentException(
                        "No url given, no DOCKER_HOST environment variable and no read/writable '/var/run/docker.sock'");
            }
        }
        String protocol = connect.contains(":" + AbstractDockerMojo.DOCKER_HTTPS_PORT) ? "https:" : "http:";
        return connect.replaceFirst("^tcp:", protocol);
    }

    public String getCertPath(String certPath) throws MojoExecutionException {
        String path = certPath != null ? certPath : getDockerMachineEnv("DOCKER_CERT_PATH");
        if (path == null) {
            File dockerHome = new File(System.getProperty("user.home") + "/.docker");
            if (dockerHome.isDirectory() && dockerHome.list(SuffixFileFilter.PEM_FILTER).length > 0) {
                return dockerHome.getAbsolutePath();
            }
        }
        return path;
    }

    public String getDockerMachineEnv(String key) throws MojoExecutionException {
        String value = System.getenv(key);
        if(value!=null) {
            return value;
        }

        String pluginContextKey = getClass().getCanonicalName();
        synchronized(pluginContext) {
            @SuppressWarnings("unchecked")
            Map<String,String> dockerEnv = (Map<String,String>)pluginContext.get(pluginContextKey);
            if(dockerEnv==null) {
                dockerEnv =  new DockerEnvironment(log).getEnvironment();
                pluginContext.put(pluginContextKey, dockerEnv);
            }
            return dockerEnv.get(key);
        }
    }
}
