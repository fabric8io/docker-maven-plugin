package io.fabric8.maven.docker.config.handler.compose;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import static io.fabric8.maven.docker.config.handler.compose.ComposeUtils.resolveAbsolutely;
import static io.fabric8.maven.docker.config.handler.compose.ComposeUtils.resolveComposeFileAbsolutely;


/**
 * Docker Compose handler for allowing a docker-compose file to be used
 * to specify the docker images.
 */
@Singleton
@Named(DockerComposeConfigHandler.TYPE_NAME)
public class DockerComposeConfigHandler implements ExternalConfigHandler {
    public static final String TYPE_NAME = "compose";

    @Override
    public String getType() {
        return TYPE_NAME;
    }

    @Inject
    MavenReaderFilter readerFilter;

    @Override
    @SuppressWarnings("unchecked")
    public List<ImageConfiguration> resolve(ImageConfiguration unresolvedConfig, MavenProject project, MavenSession session) {
        DockerComposeConfiguration handlerConfig = new DockerComposeConfiguration(unresolvedConfig.getExternalConfig());
        File composeFile = resolveComposeFileAbsolutely(handlerConfig.getBasedir(), handlerConfig.getComposeFile(), project);
        Map<String, DockerComposeServiceWrapper> allServices = new LinkedHashMap<>();

        // First retrieve all services from the compose file
        for (Object composeO : getComposeConfigurations(composeFile, project, session)) {
            Map<String, Object> compose = (Map<String, Object>) composeO;
            validateVersion(compose, composeFile);
            Map<String, Object> services = (Map<String, Object>) compose.get("services");
            for (Map.Entry<String, Object> entry : services.entrySet()) {
                String serviceName = entry.getKey();
                Map<String, Object> serviceDefinition = (Map<String, Object>) entry.getValue();

                allServices.put(serviceName, new DockerComposeServiceWrapper(serviceName, composeFile, serviceDefinition,
                    unresolvedConfig, resolveAbsolutely(handlerConfig.getBasedir(), project)));
            }
        }
        
        // Loop over all known services and add wait configurations where necessary
        for (DockerComposeServiceWrapper service : allServices.values()) {
            for (String dependentServiceName : service.getDependsOn()) {
                DockerComposeServiceWrapper dependentService = allServices.get(dependentServiceName);
                
                if (dependentService != null) {
                    if (service.equals(dependentService)) {
                        service.throwIllegalArgumentException("Invalid self-reference in dependent services");
                    }
                    
                    // Note: for short syntax, we don't need to do anything else
                    if (service.usesLongSyntaxDependsOn()) {
                        dependentService.enableWaitCondition(service.getWaitCondition(dependentServiceName));
                    }
                } else {
                    service.throwIllegalArgumentException("Undefined dependent service \"" + dependentServiceName + "\"");
                }
            }
        }
        
        // Now that we cross-correlated all dependencies from all services, let's build & return image configurations
        return allServices.values().stream()
            .map(svc -> buildImageConfiguration(svc, composeFile.getParentFile(), unresolvedConfig, handlerConfig))
            .collect(Collectors.toList());
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
                .sysctls(wrapper.getSysctls())
                .cmd(wrapper.getCommand())
                // cgroup_parent not supported
                // container_name is taken as an alias and ignored here for run config
                // devices not supported
                .dependsOn(wrapper.getDependsOn()) // depends_on relies that no container_name is set
                .healthcheck(wrapper.getHealthCheckConfiguration())
                .wait(wrapper.getWaitConfiguration())
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
                .platform(wrapper.getPlatform())
                .network(wrapper.getNetworkConfig()) // TODO: Up to now only a single network is supported and not ipv4, ipv6
                // pid not supported
                .ports(wrapper.getPortMapping())
                // security_opt not supported
                // stop_signal not supported
                .ulimits(wrapper.getUlimits())
                .volumes(wrapper.getVolumeConfig())
                .isolation(wrapper.getIsolation())
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
