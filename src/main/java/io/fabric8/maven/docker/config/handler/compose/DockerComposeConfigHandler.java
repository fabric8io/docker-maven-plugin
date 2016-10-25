package io.fabric8.maven.docker.config.handler.compose;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.fabric8.maven.docker.config.*;
import io.fabric8.maven.docker.config.handler.ExternalConfigHandler;
import io.fabric8.maven.docker.config.handler.ExternalConfigHandlerException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.yaml.snakeyaml.Yaml;


/**
 * Features not possible:
 * - wait
 * - watch
 * - detailed log config (color, extra file, ...)
 */

// Moved temporarily to resources/META-INF/plexus/components.xml because of https://github.com/codehaus-plexus/plexus-containers/issues/4
// @Component(role = ExternalConfigHandler.class)
public class DockerComposeConfigHandler implements ExternalConfigHandler {

    @Override
    public String getType() {
        return "compose";
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ImageConfiguration> resolve(ImageConfiguration unresolvedConfig, MavenProject project) {
        List<ImageConfiguration> resolved = new ArrayList<>();

        DockerComposeConfiguration config = new DockerComposeConfiguration(unresolvedConfig.getExternalConfig());
        File composeFile = resolveComposeFile(config.getBasedir(), config.getComposeFile(), project);

        for (Object composeO : getComposeConfigurations(composeFile)) {
            Map<String, Object> compose = (Map<String, Object>) composeO;
            validateVersion(compose, composeFile);
            Map<String, Object> services = (Map<String, Object>) compose.get("services");
            for (Map.Entry<String, Object> entry : services.entrySet()) {
                String serviceName = entry.getKey();
                Map<String, Object> serviceDefinition = (Map<String, Object>) entry.getValue();

                DockerComposeServiceWrapper mapper = new DockerComposeServiceWrapper(serviceName, composeFile, serviceDefinition, unresolvedConfig);
                resolved.add(buildImageConfiguration(mapper, composeFile.getParentFile()));
            }
        }

        return resolved;
    }

    private void validateVersion(Map<String, Object> compose, File file) {
        Object version = compose.get("version");
        if (version == null || !version.toString().trim().equals("2")) {
            throw new ExternalConfigHandlerException("Only version 2 of the docker-compose format is supported for " + file);
        }
    }

    private String extractDockerFilePath(DockerComposeServiceWrapper mapper, File parentDir) {
        if (mapper.requiresBuild()) {
            File buildDir = new File(mapper.getBuildDir());
            String dockerFile = mapper.getDockerfile();
            if (dockerFile == null) {
                dockerFile = "Dockerfile";
            }
            File ret = new File(buildDir, dockerFile);
            return ret.isAbsolute() ? ret.getAbsolutePath() : new File(parentDir, ret.getPath()).getAbsolutePath();
        } else {
            return null;
        }
    }

    private ImageConfiguration buildImageConfiguration(DockerComposeServiceWrapper mapper, File composeParent) {
        return new ImageConfiguration.Builder()
                .name(mapper.getImage())
                .alias(mapper.getAlias())
                .buildConfig(createBuildImageConfiguration(mapper, composeParent))
                .runConfig(createRunConfiguration(mapper))
                .build();
    }

    private Iterable<Object> getComposeConfigurations(File composePath) {
        try {
            FileInputStream stream = new FileInputStream(composePath);
            Yaml yaml = new Yaml();
            return yaml.loadAll(stream);
        }
        catch (FileNotFoundException e) {
            throw new ExternalConfigHandlerException("failed to load external configuration: " + composePath, e);
        }
    }

    private BuildImageConfiguration createBuildImageConfiguration(DockerComposeServiceWrapper mapper, File composeParent) {
        if (!mapper.requiresBuild()) {
            return null;
        }

        // TODO: Think about assembly
        return new BuildImageConfiguration.Builder()
                .dockerFile(extractDockerFilePath(mapper, composeParent))
                .args(mapper.getBuildArgs())
                .cleanup(mapper.getCleanup().toParameter())
                .compression(mapper.getCompression().name())
                .skip(mapper.getSkipBuild())
                .build();
    }

    private RunImageConfiguration createRunConfiguration(DockerComposeServiceWrapper wrapper) {
        return new RunImageConfiguration.Builder()
                .capAdd(wrapper.getCapAdd())
                .capDrop(wrapper.getCapDrop())
                .cmd(wrapper.getCommand())
                // cgroup_parent not supported
                // container_name is taken as an alias and ignored here for run config
                // devices not supported
                .dependsOn(wrapper.getDependsOn()) // depends_on relies that no container_name is set
                .dns(wrapper.getDns())
                .dnsSearch(wrapper.getDnsSearch())
                // tmpfs not supported
                .entrypoint(wrapper.getEntrypoint())
                // env_file not supported
                .env(wrapper.getEnvironment())
                // expose (for running containers) not supported
                // extends not supported
                .extraHosts(wrapper.getExtraHosts())
                // image added as top-level
                .labels(wrapper.getLabels())
                .links(wrapper.getLinks()) // external_links and links are handled the same in d-m-p
                .log(wrapper.getLogConfiguration())
                .network(wrapper.getNetworkConfig()) // TODO: Up to now only a single network is supported and not ipv4, ipv6
                // pid not supported
                .ports(wrapper.getPortMapping())
                // security_opt not supported
                // stop_signal not supported
                .ulimits(wrapper.getUlimits())
                .volumes(wrapper.getVolumeConfig())
                // cpu_share n.s.
                // cpu_quota n.s.
                // cpuset n.s.
                .domainname(wrapper.getDomainname())
                .hostname(wrapper.getHostname())
                // ipc n.s.
                // mac_address n.s.
                .memory(wrapper.getMemory())
                .memorySwap(wrapper.getMemorySwap())
                .privileged(wrapper.getPrivileged())
                // read_only n.s.
                .restartPolicy(wrapper.getRestartPolicy())
                .shmSize(wrapper.getShmSize())
                // stdin_open n.s.
                // tty n.s.
                .user(wrapper.getUser())
                .workingDir(wrapper.getWorkingDir())
                // =============================================
                // d-m-p properties:
                .namingStrategy(wrapper.getNamingStrategy())
                .portPropertyFile(wrapper.getPortPropertyFile())
                .skip(wrapper.getSkipRun())
                .build();
    }

    private File resolveComposeFile(String baseDir, String compose, MavenProject project) {
        File yamlFile = new File(compose);
        return yamlFile.isAbsolute() ? yamlFile :  new File(new File(project.getBasedir(),baseDir),compose);
    }
}
