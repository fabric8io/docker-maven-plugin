package org.jolokia.docker.maven.config.handler.compose;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.config.external.DockerComposeConfiguration;
import org.jolokia.docker.maven.config.handler.ExternalConfigHandler;
import org.jolokia.docker.maven.config.handler.ExternalConfigHandlerException;
import org.yaml.snakeyaml.Yaml;

public class DockerComposeConfigHandler implements ExternalConfigHandler {

    @Override
    public String getType() {
        return "compose";
    }

    @Override
    public List<ImageConfiguration> resolve(ImageConfiguration unresolvedConfig, Properties properties) {
        List<ImageConfiguration> resolved = new ArrayList<>();

        DockerComposeConfiguration composeConfig = unresolvedConfig.getExternalConfiguration().getComposeConfiguration();
        Map<String, DockerComposeConfiguration.Service> serviceMap = composeConfig.getServiceMap();

        for (Object configuration : configurations(composeConfig.getComposeFilePath())) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) configuration;

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String service = entry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> values = (Map<String, Object>) entry.getValue();
                
                DockerComposeValueProvider provider = new DockerComposeValueProvider(service, values, serviceMap.get(service));
                resolved.add(buildImageConfiguration(provider));
            }
        }

        return resolved;
    }

    private ImageConfiguration buildImageConfiguration(DockerComposeValueProvider provider) {
        return new ImageConfiguration.Builder()
                .name(provider.getImage())
                .alias(provider.getAlias())
                .runConfig(createRunConfiguration(provider))
                .buildConfig(createBuildImageConfiguration(provider))
                .watchConfig(createWatchImageConfiguration(provider))
                .build();
    }

    private Iterable<Object> configurations(String composePath) {
        try {
            FileInputStream stream = new FileInputStream(composePath);
            Yaml yaml = new Yaml();

            return yaml.loadAll(stream);
        }
        catch (FileNotFoundException e) {
            throw new ExternalConfigHandlerException("failed to load external configuration: " + composePath, e);
        }
    }

    private BuildImageConfiguration createBuildImageConfiguration(DockerComposeValueProvider provider) {
        return null;
    }

    private RestartPolicy createRestartPolicy(DockerComposeValueProvider provider) {
        return new RestartPolicy.Builder()
                .name(provider.getRestartPolicyName())
                .retry(provider.getRestartPolicyRetry())
                .build();
    }

    private RunImageConfiguration createRunConfiguration(DockerComposeValueProvider provider) {
        return new RunImageConfiguration.Builder()
                .capAdd(provider.getCapAdd())
                .capDrop(provider.getCapDrop())
                .cmd(provider.getCommand())
                .dns(provider.getDns())
                .dnsSearch(provider.getDnsSearch())
                .domainname(provider.getDomainName())
                .entrypoint(provider.getEntrypoint())
                .env(provider.getEnvironment())
                .envPropertyFile(provider.getEnvPropertyFile())
                .extraHosts(provider.getExtraHosts())
                .hostname(provider.getHostname())
                .labels(provider.getLabels())
                .links(provider.getLinks())
                .memory(provider.getMemory())
                .memorySwap(provider.getMemorySwap())
                .namingStrategy(provider.getNamingStrategy())
                .portPropertyFile(provider.getPortPropertyFile())
                .ports(provider.getRunPorts())
                .privileged(provider.getPrivileged())
                .restartPolicy(createRestartPolicy(provider))
                .user(provider.getUser())
                .workingDir(provider.getWorkingDir())
                .wait(createWaitConfig(provider))
                .volumes(createVolumeConfig(provider))
                .skip(provider.getSkipRun())
                .build();
    }

    private VolumeConfiguration createVolumeConfig(DockerComposeValueProvider provider) {
        return new VolumeConfiguration.Builder()
                .bind(provider.getBindVolumes())
                .from(provider.getVolumesFrom())
                .build();
    }

    private WaitConfiguration createWaitConfig(DockerComposeValueProvider provider) {
        return provider.getWaitConfiguration();
    }

    private WatchImageConfiguration createWatchImageConfiguration(DockerComposeValueProvider provider) {
        return provider.getWatchImageConfiguration();
    }
}
