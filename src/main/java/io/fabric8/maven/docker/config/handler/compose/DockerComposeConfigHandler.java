package io.fabric8.maven.docker.config.handler.compose;

import java.io.*;
import java.util.*;

import io.fabric8.maven.docker.config.*;
import io.fabric8.maven.docker.config.handler.ExternalConfigHandler;
import io.fabric8.maven.docker.config.handler.ExternalConfigHandlerException;
import io.fabric8.maven.docker.util.DeepCopy;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenReaderFilter;
import org.apache.maven.shared.filtering.MavenReaderFilterRequest;
import org.yaml.snakeyaml.Yaml;

import static io.fabric8.maven.docker.config.handler.compose.ComposeUtils.resolveAbsolutely;
import static io.fabric8.maven.docker.config.handler.compose.ComposeUtils.resolveComposeFileAbsolutely;


/**
 * Docker Compose handler for allowing a docker-compose file to be used
 * to specify the docker images.
 */

// Moved temporarily to resources/META-INF/plexus/components.xml because of https://github.com/codehaus-plexus/plexus-containers/issues/4
// @Component(role = ExternalConfigHandler.class)
public class DockerComposeConfigHandler implements ExternalConfigHandler {

    @Override
    public String getType() {
        return "compose";
    }

    // Enable later when issue above is fixed. In the meantime its declared in the components.xml, too
    // @Requirement(role = MavenReaderFilter.class)
    MavenReaderFilter readerFilter;

    @Override
    @SuppressWarnings("unchecked")
    public List<ImageConfiguration> resolve(ImageConfiguration unresolvedConfig, MavenProject project, MavenSession session) {
        List<ImageConfiguration> resolved = new ArrayList<>();

        DockerComposeConfiguration handlerConfig = new DockerComposeConfiguration(unresolvedConfig.getExternalConfig());
        File composeFile = resolveComposeFileAbsolutely(handlerConfig.getBasedir(), handlerConfig.getComposeFile(), project);

        for (Object composeO : getComposeConfigurations(composeFile, project, session)) {
            Map<String, Object> compose = (Map<String, Object>) composeO;
            validateVersion(compose, composeFile);
            Map<String, Object> services = (Map<String, Object>) compose.get("services");
            for (Map.Entry<String, Object> entry : services.entrySet()) {
                String serviceName = entry.getKey();
                Map<String, Object> serviceDefinition = (Map<String, Object>) entry.getValue();

                DockerComposeServiceWrapper mapper = new DockerComposeServiceWrapper(serviceName, composeFile, serviceDefinition, unresolvedConfig, resolveAbsolutely(handlerConfig.getBasedir(), project));
                resolved.add(buildImageConfiguration(mapper, composeFile.getParentFile(), unresolvedConfig, handlerConfig));
            }
        }

        return resolved;
    }

    private void validateVersion(Map<String, Object> compose, File file) {
        Object version = compose.get("version");
        if (version == null || !isVersion2(version.toString().trim())) {
            throw new ExternalConfigHandlerException("Only version 2.x of the docker-compose format is supported for " + file);
        }
    }

    private boolean isVersion2(String version) {
        return version.equals("2") || version.startsWith("2.");
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

    private ImageConfiguration buildImageConfiguration(DockerComposeServiceWrapper mapper,
                                                       File composeParent,
                                                       ImageConfiguration unresolvedConfig,
                                                       DockerComposeConfiguration handlerConfig) {
        ImageConfiguration.Builder builder = new ImageConfiguration.Builder()
                .name(getImageName(mapper, unresolvedConfig))
                .alias(mapper.getAlias())
                .buildConfig(createBuildImageConfiguration(mapper, composeParent, unresolvedConfig, handlerConfig))
                .runConfig(createRunConfiguration(mapper, unresolvedConfig));
        if (serviceMatchesAlias(mapper, unresolvedConfig)) {
            builder.watchConfig(DeepCopy.copy(unresolvedConfig.getWatchConfiguration()));
        }
        return builder.build();
    }

    private String getImageName(DockerComposeServiceWrapper mapper, ImageConfiguration unresolvedConfig) {
        String name = mapper.getImage();
        if (name != null) {
            return name;
        } else if (unresolvedConfig.getAlias() != null && unresolvedConfig.getAlias().equals(mapper.getAlias())) {
            return unresolvedConfig.getName();
        } else {
            return null;
        }
    }

    private Iterable<Object> getComposeConfigurations(File composePath, MavenProject project, MavenSession session) {
        try {
            Yaml yaml = new Yaml();
            return yaml.loadAll(getFilteredReader(composePath, project, session));
        }
        catch (FileNotFoundException | MavenFilteringException e) {
            throw new ExternalConfigHandlerException("failed to load external configuration: " + composePath, e);
        }
    }

    private Reader getFilteredReader(File path, MavenProject project, MavenSession session) throws FileNotFoundException, MavenFilteringException {
        MavenReaderFilterRequest request =
            new MavenReaderFilterRequest(
                new FileReader(path),
                true,
                project,
                Collections.<String>emptyList(),
                false,
                session,
                null);
        //request.setEscapeString("$");
        return readerFilter.filter(request);
    }

    private BuildImageConfiguration createBuildImageConfiguration(DockerComposeServiceWrapper mapper,
                                                                  File composeParent,
                                                                  ImageConfiguration imageConfig,
                                                                  DockerComposeConfiguration handlerConfig) {
        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
        if (handlerConfig.isIgnoreBuild() || !mapper.requiresBuild()) {
            if (serviceMatchesAlias(mapper, imageConfig)) {
                // Only when the specified image name maps to the current docker-compose service
                return buildConfig;
            } else {
                return null;
            }
        }

        // Build from the specification as given in the docker-compose file
        BuildImageConfiguration.Builder builder = new BuildImageConfiguration.Builder(buildConfig)
                .dockerFile(extractDockerFilePath(mapper, composeParent))
                .args(mapper.getBuildArgs());
        return builder.build();
    }

    private boolean serviceMatchesAlias(DockerComposeServiceWrapper mapper, ImageConfiguration imageConfig) {
        return mapper.getAlias() != null && mapper.getAlias().equals(imageConfig.getAlias());
    }

    private RunImageConfiguration createRunConfiguration(DockerComposeServiceWrapper wrapper, ImageConfiguration imageConfig) {
        RunImageConfiguration.Builder builder =
            serviceMatchesAlias(wrapper, imageConfig) ?
                new RunImageConfiguration.Builder(imageConfig.getRunConfiguration()) :
                new RunImageConfiguration.Builder();
        return builder
                .capAdd(wrapper.getCapAdd())
                .capDrop(wrapper.getCapDrop())
                .cmd(wrapper.getCommand())
                // cgroup_parent not supported
                // container_name is taken as an alias and ignored here for run config
                // devices not supported
                .dependsOn(wrapper.getDependsOn()) // depends_on relies that no container_name is set
                .dns(wrapper.getDns())
                .dnsSearch(wrapper.getDnsSearch())
                .tmpfs(wrapper.getTmpfs())
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
                .cpuShares(wrapper.getCpuShares())
                .cpus(wrapper.getCpusCount())
                .cpuSet(wrapper.getCpuSet())
                // cpu_quota n.s.
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
                .build();
    }

}
