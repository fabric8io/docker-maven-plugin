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
import org.yaml.snakeyaml.Yaml;

public class DockerComposeConfigHandler implements ExternalConfigHandler {

    private static final ImageConfiguration EMPTY = new ImageConfiguration.Builder().build();

    @Override
    public String getType() {
        return "compose";
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ImageConfiguration> resolve(ImageConfiguration unresolvedConfig, MavenProject project) {
        DockerComposeConfiguration config = new DockerComposeConfiguration(unresolvedConfig.getExternalConfig());

        String resolvedComposePath = resolveFilePath(config.getBasedir(), config.getComposeFile(), project);
        String resolvedComposeParent = new File(resolvedComposePath).getParent();

        List<ImageConfiguration> resolved = new ArrayList<>();
        for (Object configuration : configurations(resolvedComposePath)) {
            Map<String, Object> map = (Map<String, Object>) configuration;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String service = entry.getKey();
                Map<String, Object> values = (Map<String, Object>) entry.getValue();

                DockerComposeValueProvider provider =
                        new DockerComposeValueProvider(service, values);
                resolved.add(buildImageConfiguration(provider, resolvedComposeParent));
            }
        }

        return resolved;
    }

    String buildDockerFileDir(DockerComposeValueProvider provider, String resolvedComposePath) {
        String buildDir = provider.getBuildDir();

        if (".".equals(buildDir)) {
            return resolvedComposePath;
        }

        File file = new File(buildDir);
        if (file.isAbsolute()) {
            return buildDir;
        }

        return new File(resolvedComposePath, buildDir).toString();
    }

    private ImageConfiguration buildImageConfiguration(DockerComposeValueProvider provider, String resolvedComposeParent) {
        return new ImageConfiguration.Builder()
                .name(provider.getImage())
                .alias(provider.getAlias())
                .runConfig(createRunConfiguration(provider))
                .buildConfig(createBuildImageConfiguration(provider, resolvedComposeParent))
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

    private BuildImageConfiguration createBuildImageConfiguration(DockerComposeValueProvider provider, String resolvedComposeParent) {
        if (!provider.requiresBuild()) {
            return null;
        }

        String dockerFileDir = buildDockerFileDir(provider, resolvedComposeParent);

        AssemblyConfiguration assembly = new AssemblyConfiguration.Builder()
                .dockerFileDir(dockerFileDir)
                .build();

        return new BuildImageConfiguration.Builder()
                .assembly(assembly)
                .cleanup(provider.getCleanup())
                .compression(provider.getCompression())
                .skip(provider.getSkipBuild())
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
                .restartPolicy(provider.getRestartPolicy())
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

    private String resolveFilePath(String baseDir, String compose, MavenProject project) {
        File yamlFile = new File(compose);
        return yamlFile.isAbsolute() ?
            yamlFile.getAbsolutePath() :
            new File(new File(project.getBasedir(),baseDir),compose).getAbsolutePath();
    }
}
