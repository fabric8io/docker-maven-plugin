package io.fabric8.maven.docker.service;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerConnectionDetector;
import io.fabric8.maven.docker.access.DockerMachine;
import io.fabric8.maven.docker.access.hc.DockerAccessWithHcClient;
import io.fabric8.maven.docker.config.DockerMachineConfiguration;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;

/**
 *
 */
@Component(role = DockerAccessFactory.class, instantiationStrategy = "singleton")
public class DockerAccessFactory {

    // Minimal API version, independent of any feature used
    public static final String API_VERSION = "1.18";

    public DockerAccess createDockerAccess(DockerAccessContext dockerAccessContext, Logger log) throws MojoExecutionException, MojoFailureException {

        try {
            DockerConnectionDetector dockerConnectionDetector = createDockerConnectionDetector(dockerAccessContext, log);
            DockerConnectionDetector.ConnectionParameter connectionParam =
                    dockerConnectionDetector.detectConnectionParameter(dockerAccessContext.getDockerHost(), dockerAccessContext.getCertPath());
            String version = dockerAccessContext.getMinimalApiVersion() != null ? dockerAccessContext.getMinimalApiVersion() : API_VERSION;
            DockerAccess access = new DockerAccessWithHcClient("v" + version, connectionParam.getUrl(),
                    connectionParam.getCertPath(),
                    dockerAccessContext.getMaxConnections(),
                    log);
            access.start();
            setDockerHostAddressProperty(dockerAccessContext, connectionParam.getUrl());
            String serverVersion = access.getServerApiVersion();
            if (!EnvUtil.greaterOrEqualsVersion(serverVersion,version)) {
                throw new MojoExecutionException(
                        String.format("Server API version %s is smaller than required API version %s", serverVersion, version));
            }
            return access;
        }
        catch (IOException e) {
            throw new MojoExecutionException("Cannot create docker access object ", e);
        }

    }

    private DockerConnectionDetector createDockerConnectionDetector(DockerAccessContext dockerAccessContext, Logger log) {
        return new DockerConnectionDetector(getDockerHostProviders(dockerAccessContext, log));
    }

    private List<DockerConnectionDetector.DockerHostProvider> getDockerHostProviders(DockerAccessContext dockerAccessContext, Logger log) {
        if (dockerAccessContext.getDockerHostProviders() != null) {
            return dockerAccessContext.getDockerHostProviders();
        }

        return getDefaultDockerHostProviders(dockerAccessContext, log);
    }

    /**
     * Return a list of providers which could delive connection parameters from
     * calling external commands. For this plugin this is docker-machine, but can be overridden
     * to add other config options, too.
     *
     * @return list of providers or <code>null</code> if none are applicable
     */
    private List<DockerConnectionDetector.DockerHostProvider> getDefaultDockerHostProviders(DockerAccessContext dockerAccessContext, Logger log) {
        DockerMachineConfiguration config = dockerAccessContext.getMachine();
        if (config == null) {
            Properties projectProps = dockerAccessContext.getMavenProject().getProperties();
            if (!dockerAccessContext.isSkipMachine()) {
                if (projectProps.containsKey(DockerMachineConfiguration.DOCKER_MACHINE_NAME_PROP)) {
                    config = new DockerMachineConfiguration(
                            projectProps.getProperty(DockerMachineConfiguration.DOCKER_MACHINE_NAME_PROP),
                            projectProps.getProperty(DockerMachineConfiguration.DOCKER_MACHINE_AUTO_CREATE_PROP));
                }
            }
        }

        List<DockerConnectionDetector.DockerHostProvider> ret = new ArrayList<>();
        ret.add(new DockerMachine(log, config));
        return ret;
    }

    // Registry for managed containers
    private void setDockerHostAddressProperty(DockerAccessContext dockerAccessContext, String dockerUrl) throws MojoFailureException {
        Properties props = dockerAccessContext.getMavenProject().getProperties();
        if (props.getProperty("docker.host.address") == null) {
            final String host;
            try {
                URI uri = new URI(dockerUrl);
                if (uri.getHost() == null && (uri.getScheme().equals("unix") || uri.getScheme().equals("npipe"))) {
                    host = "localhost";
                } else {
                    host = uri.getHost();
                }
            } catch (URISyntaxException e) {
                throw new MojoFailureException("Cannot parse " + dockerUrl + " as URI: " + e.getMessage(), e);
            }
            props.setProperty("docker.host.address", host == null ? "" : host);
        }
    }

    // ===========================================

    public static class DockerAccessContext implements Serializable {

        private MavenProject mavenProject;

        private DockerMachineConfiguration machine;

        private List<DockerConnectionDetector.DockerHostProvider> dockerHostProviders;

        private boolean skipMachine;

        private String minimalApiVersion;

        private String dockerHost;

        private String certPath;

        private int maxConnections = 100;

        public DockerAccessContext() {}

        public MavenProject getMavenProject() {
            return mavenProject;
        }

        public void setMavenProject(MavenProject mavenProject) {
            this.mavenProject = mavenProject;
        }

        public DockerMachineConfiguration getMachine() {
            return machine;
        }

        public void setMachine(DockerMachineConfiguration machine) {
            this.machine = machine;
        }

        public List<DockerConnectionDetector.DockerHostProvider> getDockerHostProviders() {
            return dockerHostProviders;
        }

        public void setDockerHostProviders(List<DockerConnectionDetector.DockerHostProvider> dockerHostProviders) {
            this.dockerHostProviders = dockerHostProviders;
        }

        public boolean isSkipMachine() {
            return skipMachine;
        }

        public void setSkipMachine(boolean skipMachine) {
            this.skipMachine = skipMachine;
        }

        public String getMinimalApiVersion() {
            return minimalApiVersion;
        }

        public void setMinimalApiVersion(String minimalApiVersion) {
            this.minimalApiVersion = minimalApiVersion;
        }

        public String getDockerHost() {
            return dockerHost;
        }

        public void setDockerHost(String dockerHost) {
            this.dockerHost = dockerHost;
        }

        public String getCertPath() {
            return certPath;
        }

        public void setCertPath(String certPath) {
            this.certPath = certPath;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        // ===========================================

        public static class Builder {

            private DockerAccessContext context = new DockerAccessContext();

            public Builder() {
                this.context = new DockerAccessContext();
            }

            public Builder mavenProject(MavenProject mavenProject) {
                context.setMavenProject(mavenProject);
                return this;
            }

            public Builder machine(DockerMachineConfiguration machine) {
                context.setMachine(machine);
                return this;
            }

            public Builder dockerHostProviders(List<DockerConnectionDetector.DockerHostProvider> dockerHostProviders) {
                context.setDockerHostProviders(dockerHostProviders);
                return this;
            }

            public Builder skipMachine(boolean skipMachine) {
                context.setSkipMachine(skipMachine);
                return this;
            }

            public Builder minimalApiVersion(String minimalApiVersion) {
                context.setMinimalApiVersion(minimalApiVersion);
                return this;
            }

            public Builder dockerHost(String dockerHost) {
                context.setDockerHost(dockerHost);
                return this;
            }

            public Builder certPath(String certPath) {
                context.setCertPath(certPath);
                return this;
            }

            public Builder maxConnections(int maxConnections) {
                context.setMaxConnections(maxConnections);
                return this;
            }

            public DockerAccessContext build() {
                return context;
            }

        }
    }

}
