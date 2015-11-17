//package org.jolokia.docker.maven.config.handler;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Properties;
//
//import org.jolokia.docker.maven.config.AssemblyConfiguration;
//import org.jolokia.docker.maven.config.BuildImageConfiguration;
//import org.jolokia.docker.maven.config.ImageConfiguration;
//import org.jolokia.docker.maven.config.RestartPolicy;
//import org.jolokia.docker.maven.config.RunImageConfiguration;
//import org.jolokia.docker.maven.config.VolumeConfiguration;
//import org.jolokia.docker.maven.config.WaitConfiguration;
//import org.jolokia.docker.maven.config.WatchImageConfiguration;
//
//public abstract class AbstractExternalConfigHandler implements ExternalConfigHandler {
//
//    @Override
//    public List<ImageConfiguration> resolve(ImageConfiguration unresolvedConfig, Properties properties)
//        throws ExternalConfigHandlerException {
//
//        List<ImageConfiguration> resolved = new ArrayList<>();
//
//        for (ExternalConfigValueProvider provider : getImageValueProviders(unresolvedConfig, properties)) {
//            resolved.add(buildImageConfiguration(provider));
//        }
//
//        return resolved;
//    }
//
//    private ImageConfiguration buildImageConfiguration(ExternalConfigValueProvider provider) {
//        return new ImageConfiguration.Builder()
//                .name(provider.getName())
//                .alias(provider.getAlias())
//                .buildConfig(createBuildConfiguration(provider))
//                .runConfig(createRunConfiguration(provider))
//                .watchConfig(createWatchConfiguration(provider))
//                .build();
//    }
//
//    protected abstract List<? extends ExternalConfigValueProvider> getImageValueProviders(ImageConfiguration unresolvedConfig,
//            Properties properties);
//
//    private RunImageConfiguration createRunConfiguration(ExternalConfigValueProvider provider) {
//
//        return new RunImageConfiguration.Builder()
//                .capAdd(provider.getCapAdd())
//                .capDrop(provider.getCapDrop())
//                .cmd(provider.getCommand())
//                .dns(provider.getDns())
//                .dnsSearch(provider.getDnsSearch())
//                .domainname(provider.getDomainName())
//                .entrypoint(provider.getEntrypoint())
//                .env(provider.getEnvironment())
//                .labels(provider.getLabels())
//                .envPropertyFile(provider.getEnvPropertyFile())
//                .extraHosts(provider.getExtraHosts())
//                .hostname(provider.getHostname())
//                .links(provider.getLinks())
//                .memory(provider.getMemory())
//                .memorySwap(provider.getMemorySwap())
//                .namingStrategy(provider.getNamingStrategy())
//                .portPropertyFile(provider.getPortPropertyFile())
//                .ports(provider.getRunPorts())
//                .privileged(provider.getPrivileged())
//                .restartPolicy(createRestartPolicy(provider))
//                .user(provider.getUser())
//                .workingDir(provider.getWorkingDir())
//                .wait(createWaitConfig(provider))
//                .volumes(createVolumeConfig(provider))
//                .skip(provider.getSkipRun())
//                .build();
//    }
//
//    private RestartPolicy createRestartPolicy(ExternalConfigValueProvider provider) {
//        return new RestartPolicy.Builder()
//                .name(provider.getRestartPolicyName())
//                .retry(provider.getRestartPolicyRetry())
//                .build();
//    }
//
//    private VolumeConfiguration createVolumeConfig(ExternalConfigValueProvider provider) {
//        return new VolumeConfiguration.Builder()
//                .bind(provider.getBindVolumes())
//                .from(provider.getVolumesFrom())
//                .build();
//    }
//
//    private BuildImageConfiguration createBuildConfiguration(ExternalConfigValueProvider provider) {
//        return new BuildImageConfiguration.Builder()
//                .cmd(provider.getCommand())
//                .cleanup(provider.getCleanup())
//                .optimise(provider.getOptimise())
//                .entryPoint(provider.getEntrypoint())
//                .assembly(createAssembly(provider))
//                .env(provider.getEnvironment())
//                .labels(provider.getLabels())
//                .ports(provider.getBuildPorts())
//                .runCmds(provider.getRunCommands())
//                .from(provider.getFrom())
//                .registry(provider.getRegistry())
//                .volumes(provider.getVolumes())
//                .tags(provider.getTags())
//                .maintainer(provider.getMaintainer())
//                .workdir(provider.getWorkdir())
//                .skip(provider.getSkipBuild())
//                .build();
//    }
//
//    private AssemblyConfiguration createAssembly(ExternalConfigValueProvider provider) {
//        return new AssemblyConfiguration.Builder()
//                .basedir(provider.getAssemblyBasedir())
//                .descriptor(provider.getAssemblyDescriptor())
//                .descriptorRef(provider.getAssemblyDescriptorRef())
//                .dockerFileDir(provider.getDockerFileDir())
//                .exportBasedir(provider.getAssemblyExortBasedir())
//                .ignorePermissions(provider.getAssemblyIgnorePermissions())
//                .user(provider.getAssemblyUser())
//                .mode(provider.getAssemblyMode())
//                .build();
//    }
//
//    private WaitConfiguration createWaitConfig(ExternalConfigValueProvider provider) {
//        return new WaitConfiguration.Builder()
//                .time(provider.getWaitTime())
//                .url(provider.getHttpWaitUrl())
//                .preStop(provider.getWaitPreStop())
//                .postStart(provider.getWaitPostStart())
//                .method(provider.getWaitHttpMethod())
//                .status(provider.getWaitHttpStatus())
//                .log(provider.getWaitLog())
//                .kill(provider.getWaitKill())
//                .shutdown(provider.getWaitShutdown())
//                .build();
//    }
//
//    private WatchImageConfiguration createWatchConfiguration(ExternalConfigValueProvider provider) {
//        return new WatchImageConfiguration.Builder()
//                .interval(provider.getWatchInterval())
//                .postGoal(provider.getWatchPostGoal())
//                .mode(provider.getWatchMode())
//                .build();
//    }
//}
