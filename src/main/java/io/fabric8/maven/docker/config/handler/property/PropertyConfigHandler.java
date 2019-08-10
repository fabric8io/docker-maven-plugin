package io.fabric8.maven.docker.config.handler.property;/*
 *
 * Copyright 2014 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.HealthCheckConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.LogConfiguration;
import io.fabric8.maven.docker.config.NetworkConfig;
import io.fabric8.maven.docker.config.RestartPolicy;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.config.RunVolumeConfiguration;
import io.fabric8.maven.docker.config.UlimitConfig;
import io.fabric8.maven.docker.config.WaitConfiguration;
import io.fabric8.maven.docker.config.WatchImageConfiguration;
import io.fabric8.maven.docker.config.handler.ExternalConfigHandler;
import io.fabric8.maven.docker.util.EnvUtil;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.CollectionUtils;

import static io.fabric8.maven.docker.config.handler.property.ConfigKey.*;

/**
 * @author roland
 * @since 18/11/14
 */
// Moved temporarily to resources/META-INF/plexus/components.xml because of https://github.com/codehaus-plexus/plexus-containers/issues/4
// @Component(role = ExternalConfigHandler.class)
public class PropertyConfigHandler implements ExternalConfigHandler {

    public static final String TYPE_NAME = "properties";
    public static final String DEFAULT_PREFIX = "docker";

    @Override
    public String getType() {
        return TYPE_NAME;
    }

    @Override
    public List<ImageConfiguration> resolve(ImageConfiguration fromConfig, MavenProject project, MavenSession session)
        throws IllegalArgumentException {
        Map<String, String> externalConfig = fromConfig.getExternalConfig();
        String prefix = getPrefix(externalConfig);
        Properties properties = EnvUtil.getPropertiesWithSystemOverrides(project);
        PropertyMode propertyMode = getMode(externalConfig);
        ValueProvider valueProvider = new ValueProvider(prefix, properties, propertyMode);

        RunImageConfiguration run = extractRunConfiguration(fromConfig, valueProvider);
        BuildImageConfiguration build = extractBuildConfiguration(fromConfig, valueProvider, project);
        WatchImageConfiguration watch = extractWatchConfig(fromConfig, valueProvider);
        String name = valueProvider.getString(NAME, fromConfig.getName());
        String alias = valueProvider.getString(ALIAS, fromConfig.getAlias());
        String removeNamePattern = valueProvider.getString(REMOVE_NAME_PATTERN, fromConfig.getRemoveNamePattern());
        String stopNamePattern = valueProvider.getString(STOP_NAME_PATTERN, fromConfig.getStopNamePattern());

        if (name == null) {
            throw new IllegalArgumentException(String.format("Mandatory property [%s] is not defined", NAME));
        }

        return Collections.singletonList(
                new ImageConfiguration.Builder()
                        .name(name)
                        .alias(alias)
                        .removeNamePattern(removeNamePattern)
                        .stopNamePattern(stopNamePattern)
                        .runConfig(run)
                        .buildConfig(build)
                        .watchConfig(watch)
                        .build());
    }

    private boolean isStringValueNull(ValueProvider valueProvider, BuildImageConfiguration config, ConfigKey key, Supplier<String> supplier) {
        return valueProvider.getString(key, config == null ? null : supplier.get()) != null;
    }
    // Enable build config only when a `.from.`, `.dockerFile.`, or `.dockerFileDir.` is configured
    private boolean buildConfigured(BuildImageConfiguration config, ValueProvider valueProvider, MavenProject project) {


        if (isStringValueNull(valueProvider, config, FROM, () -> config.getFrom())) {
            return true;
        }

        if (valueProvider.getMap(FROM_EXT, config == null ? null : config.getFromExt()) != null) {
            return true;
        }
        if (isStringValueNull(valueProvider, config, DOCKER_FILE, () -> config.getDockerFileRaw() ))  {
            return true;
        }
        if (isStringValueNull(valueProvider, config, DOCKER_ARCHIVE, () -> config.getDockerArchiveRaw())) {
            return true;
        }

        if (isStringValueNull(valueProvider, config, CONTEXT_DIR, () -> config.getContextDirRaw())) {
            return true;
        }

        if (isStringValueNull(valueProvider, config, DOCKER_FILE_DIR, () -> config.getDockerFileDirRaw())) {
            return true;
        }

        // Simple Dockerfile mode
        return new File(project.getBasedir(),"Dockerfile").exists();
    }


    private BuildImageConfiguration extractBuildConfiguration(ImageConfiguration fromConfig, ValueProvider valueProvider, MavenProject project) {
        BuildImageConfiguration config = fromConfig.getBuildConfiguration();
        if (!buildConfigured(config, valueProvider, project)) {
            return null;
        }

        return new BuildImageConfiguration.Builder()
                .cmd(extractArguments(valueProvider, CMD, config == null ? null : config.getCmd()))
                .cleanup(valueProvider.getString(CLEANUP, config == null ? null : config.getCleanup()))
                .noCache(valueProvider.getBoolean(NO_CACHE, config == null ? null : config.getNoCache()))
                .cacheFrom(valueProvider.getString(CACHE_FROM, config == null ? null : config.getCacheFrom().toString()))
                .optimise(valueProvider.getBoolean(OPTIMISE, config == null ? null : config.getOptimise()))
                .entryPoint(extractArguments(valueProvider, ENTRYPOINT, config == null ? null : config.getEntryPoint()))
                .assembly(extractAssembly(config == null ? null : config.getAssemblyConfiguration(), valueProvider))
                .env(CollectionUtils.mergeMaps(
                        valueProvider.getMap(ENV_BUILD, config == null ? null : config.getEnv()),
                        valueProvider.getMap(ENV, Collections.<String, String>emptyMap())
                ))
                .args(valueProvider.getMap(ARGS, config == null ? null : config.getArgs()))
                .labels(valueProvider.getMap(LABELS, config == null ? null : config.getLabels()))
                .ports(extractPortValues(config == null ? null : config.getPorts(), valueProvider))
                .shell(extractArguments(valueProvider, SHELL, config == null ? null : config.getShell()))
                .runCmds(valueProvider.getList(RUN, config == null ? null : config.getRunCmds()))
                .from(valueProvider.getString(FROM, config == null ? null : config.getFrom()))
                .fromExt(valueProvider.getMap(FROM_EXT, config == null ? null : config.getFromExt()))
                .registry(valueProvider.getString(REGISTRY, config == null ? null : config.getRegistry()))
                .volumes(valueProvider.getList(VOLUMES, config == null ? null : config.getVolumes()))
                .tags(valueProvider.getList(TAGS, config == null ? null : config.getTags()))
                .maintainer(valueProvider.getString(MAINTAINER, config == null ? null : config.getMaintainer()))
                .workdir(valueProvider.getString(WORKDIR, config == null ? null : config.getWorkdir()))
                .skip(valueProvider.getBoolean(SKIP_BUILD, config == null ? null : config.getSkip()))
                .imagePullPolicy(valueProvider.getString(IMAGE_PULL_POLICY_BUILD, config == null ? null : config.getImagePullPolicy()))
                .contextDir(valueProvider.getString(CONTEXT_DIR, config == null ? null : config.getContextDirRaw()))
                .dockerArchive(valueProvider.getString(DOCKER_ARCHIVE, config == null ? null : config.getDockerArchiveRaw()))
                .loadNamePattern(valueProvider.getString(LOAD_NAME_PATTERN, config == null ? null : config.getLoadNamePattern()))
                .dockerFile(valueProvider.getString(DOCKER_FILE, config == null ? null : config.getDockerFileRaw()))
                .dockerFileDir(valueProvider.getString(DOCKER_FILE_DIR, config == null ? null : config.getDockerFileDirRaw()))
                .buildOptions(valueProvider.getMap(BUILD_OPTIONS, config == null ? null : config.getBuildOptions()))
                .filter(valueProvider.getString(FILTER, config == null ? null : config.getFilterRaw()))
                .user(valueProvider.getString(USER, config == null ? null : config.getUser()))
                .healthCheck(extractHealthCheck(config == null ? null : config.getHealthCheck(), valueProvider))
                .build();
    }

    private RunImageConfiguration extractRunConfiguration(ImageConfiguration fromConfig, ValueProvider valueProvider) {
        RunImageConfiguration config = fromConfig.getRunConfiguration();
        if (config.isDefault()) {
            config = null;
        }

        return new RunImageConfiguration.Builder()
                .capAdd(valueProvider.getList(CAP_ADD, config == null ? null : config.getCapAdd()))
                .capDrop(valueProvider.getList(CAP_DROP, config == null ? null : config.getCapDrop()))
                .securityOpts(valueProvider.getList(SECURITY_OPTS, config == null ? null : config.getSecurityOpts()))
                .cmd(extractArguments(valueProvider, CMD, config == null ? null : config.getCmd()))
                .dns(valueProvider.getList(DNS, config == null ? null : config.getDns()))
                .dependsOn(valueProvider.getList(DEPENDS_ON, config == null ? null : config.getDependsOn()))
                .net(valueProvider.getString(NET, config == null ? null : config.getNetRaw()))
                .network(extractNetworkConfig(config == null ? null : config.getNetworkingConfig(), valueProvider))
                .dnsSearch(valueProvider.getList(DNS_SEARCH, config == null ? null : config.getDnsSearch()))
                .domainname(valueProvider.getString(DOMAINNAME, config == null ? null : config.getDomainname()))
                .entrypoint(extractArguments(valueProvider, ENTRYPOINT, config == null ? null : config.getEntrypoint()))
                .env(CollectionUtils.mergeMaps(
                        valueProvider.getMap(ENV_RUN, config == null ? null : config.getEnv()),
                        valueProvider.getMap(ENV, Collections.<String, String>emptyMap())
                ))
                .labels(valueProvider.getMap(LABELS, config == null ? null : config.getLabels()))
                .envPropertyFile(valueProvider.getString(ENV_PROPERTY_FILE, config == null ? null : config.getEnvPropertyFile()))
                .extraHosts(valueProvider.getList(EXTRA_HOSTS, config == null ? null : config.getExtraHosts()))
                .hostname(valueProvider.getString(HOSTNAME, config == null ? null : config.getHostname()))
                .links(valueProvider.getList(LINKS, config == null ? null : config.getLinks()))
                .memory(valueProvider.getLong(MEMORY, config == null ? null : config.getMemory()))
                .memorySwap(valueProvider.getLong(MEMORY_SWAP, config == null ? null : config.getMemorySwap()))
                .namingStrategy(valueProvider.getString(NAMING_STRATEGY, config == null || config.getNamingStrategy() == null ? null : config.getNamingStrategy().name()))
                .exposedPropertyKey(valueProvider.getString(EXPOSED_PROPERTY_KEY, config == null ? null : config.getExposedPropertyKey()))
                .portPropertyFile(valueProvider.getString(PORT_PROPERTY_FILE, config == null ? null : config.getPortPropertyFile()))
                .ports(valueProvider.getList(PORTS, config == null ? null : config.getPorts()))
                .shmSize(valueProvider.getLong(SHMSIZE, config == null ? null : config.getShmSize()))
                .privileged(valueProvider.getBoolean(PRIVILEGED, config == null ? null : config.getPrivileged()))
                .restartPolicy(extractRestartPolicy(config == null ? null : config.getRestartPolicy(), valueProvider))
                .user(valueProvider.getString(USER, config == null ? null : config.getUser()))
                .workingDir(valueProvider.getString(WORKING_DIR, config == null ? null : config.getWorkingDir()))
                .log(extractLogConfig(config == null ? null : config.getLogConfiguration(), valueProvider))
                .wait(extractWaitConfig(config == null ? null : config.getWaitConfiguration(), valueProvider))
                .volumes(extractVolumeConfig(config == null ? null : config.getVolumeConfiguration(), valueProvider))
                .skip(valueProvider.getBoolean(SKIP_RUN, config == null ? null : config.getSkip()))
                .imagePullPolicy(valueProvider.getString(IMAGE_PULL_POLICY_RUN, config == null ? null : config.getImagePullPolicy()))
                .ulimits(extractUlimits(config == null ? null : config.getUlimits(), valueProvider))
                .tmpfs(valueProvider.getList(TMPFS, config == null ? null : config.getTmpfs()))
                .cpuShares(valueProvider.getLong(CPUSHARES, config == null ? null : config.getCpuShares()))
                .cpus(valueProvider.getLong(CPUS, config == null ? null : config.getCpus()))
                .cpuSet(valueProvider.getString(CPUSET, config == null ? null : config.getCpuSet()))
                .readOnly(valueProvider.getBoolean(READ_ONLY, config == null ? null : config.getReadOnly()))
                .autoRemove(valueProvider.getBoolean(AUTO_REMOVE, config == null ? null : config.getAutoRemove()))
                .build();
    }

    private NetworkConfig extractNetworkConfig(NetworkConfig config, ValueProvider valueProvider) {
        return new NetworkConfig.Builder()
            .mode(valueProvider.getString(NETWORK_MODE, config == null || config.getMode() == null ? null : config.getMode().name()))
            .name(valueProvider.getString(NETWORK_NAME, config == null ? null : config.getName()))
            .aliases(valueProvider.getList(NETWORK_ALIAS, config == null ? null : config.getAliases()))
            .build();
    }

    @SuppressWarnings("deprecation")
    private AssemblyConfiguration extractAssembly(AssemblyConfiguration config, ValueProvider valueProvider) {
        return new AssemblyConfiguration.Builder()
                .targetDir(valueProvider.getString(ASSEMBLY_BASEDIR, config == null ? null : config.getTargetDir()))
                .descriptor(valueProvider.getString(ASSEMBLY_DESCRIPTOR, config == null ? null : config.getDescriptor()))
                .descriptorRef(valueProvider.getString(ASSEMBLY_DESCRIPTOR_REF, config == null ? null : config.getDescriptorRef()))
                .dockerFileDir(valueProvider.getString(ASSEMBLY_DOCKER_FILE_DIR, config == null ? null : config.getDockerFileDir()))
                .exportBasedir(valueProvider.getBoolean(ASSEMBLY_EXPORT_BASEDIR, config == null ? null : config.getExportTargetDir()))
                .ignorePermissions(valueProvider.getBoolean(ASSEMBLY_IGNORE_PERMISSIONS, config == null ? null : config.getIgnorePermissions()))
                .permissions(valueProvider.getString(ASSEMBLY_PERMISSIONS, config == null ? null : config.getPermissionsRaw()))
                .user(valueProvider.getString(ASSEMBLY_USER, config == null ? null : config.getUser()))
                .mode(valueProvider.getString(ASSEMBLY_MODE, config == null ? null : config.getModeRaw()))
                .tarLongFileMode(valueProvider.getString(ASSEMBLY_TARLONGFILEMODE, config == null ? null : config.getTarLongFileMode()))
                .build();
    }

    private HealthCheckConfiguration extractHealthCheck(HealthCheckConfiguration config, ValueProvider valueProvider) {
        Map<String, String> healthCheckProperties = valueProvider.getMap(HEALTHCHECK, Collections.<String, String>emptyMap());
        if (healthCheckProperties != null && healthCheckProperties.size() > 0) {
            return new HealthCheckConfiguration.Builder()
                    .interval(valueProvider.getString(HEALTHCHECK_INTERVAL, config == null ? null : config.getInterval()))
                    .timeout(valueProvider.getString(HEALTHCHECK_TIMEOUT, config == null ? null : config.getTimeout()))
                    .startPeriod(valueProvider.getString(HEALTHCHECK_START_PERIOD, config == null ? null : config.getStartPeriod()))
                    .retries(valueProvider.getInteger(HEALTHCHECK_RETRIES, config == null ? null : config.getRetries()))
                    .mode(valueProvider.getString(HEALTHCHECK_MODE, config == null || config.getMode() == null ? null : config.getMode().name()))
                    .cmd(extractArguments(valueProvider, HEALTHCHECK_CMD, config == null ? null : config.getCmd()))
                    .build();
        } else {
            return config;
        }
    }

    // Extract only the values of the port mapping

    private List<String> extractPortValues(List<String> config, ValueProvider valueProvider) {
        List<String> ret = new ArrayList<>();
        List<String> ports = valueProvider.getList(PORTS, config);
        if (ports == null) {
            return null;
        }
        List<String[]> parsedPorts = EnvUtil.splitOnLastColon(ports);
        for (String[] port : parsedPorts) {
            ret.add(port[1]);
        }
        return ret;
    }

    private Arguments extractArguments(ValueProvider valueProvider, ConfigKey configKey, Arguments alternative) {
        return valueProvider.getObject(configKey, alternative, raw -> raw != null ? new Arguments(raw) : null);
    }

    private RestartPolicy extractRestartPolicy(RestartPolicy config, ValueProvider valueProvider) {
        return new RestartPolicy.Builder()
                .name(valueProvider.getString(RESTART_POLICY_NAME, config == null ? null : config.getName()))
                .retry(valueProvider.getInt(RESTART_POLICY_RETRY, config == null || config.getRetry() == 0 ? null : config.getRetry()))
                .build();
    }

    private LogConfiguration extractLogConfig(LogConfiguration config, ValueProvider valueProvider) {
        LogConfiguration.Builder builder = new LogConfiguration.Builder()
            .color(valueProvider.getString(LOG_COLOR, config == null ? null : config.getColor()))
            .date(valueProvider.getString(LOG_DATE, config == null ? null : config.getDate()))
            .file(valueProvider.getString(LOG_FILE, config == null ? null : config.getFileLocation()))
            .prefix(valueProvider.getString(LOG_PREFIX, config == null ? null : config.getPrefix()))
            .logDriverName(valueProvider.getString(LOG_DRIVER_NAME, config == null || config.getDriver() == null ? null : config.getDriver().getName()))
            .logDriverOpts(valueProvider.getMap(LOG_DRIVER_OPTS, config == null || config.getDriver() == null ? null : config.getDriver().getOpts()));

        Boolean configEnabled = config != null ? config.isEnabled() : null;
        Boolean enabled = valueProvider.getBoolean(LOG_ENABLED, configEnabled);
        builder.enabled(enabled);
        return builder.build();
    }

    private WaitConfiguration extractWaitConfig(WaitConfiguration config, ValueProvider valueProvider) {
        String url = valueProvider.getString(WAIT_HTTP_URL, config == null ? null : config.getUrl());
        if (url == null) {
            // Fallback to deprecated old URL
            url = valueProvider.getString(WAIT_URL, config == null ? null : config.getUrl());
        }
        WaitConfiguration.ExecConfiguration exec = config == null ? null : config.getExec();
        WaitConfiguration.TcpConfiguration tcp = config == null ? null : config.getTcp();
        WaitConfiguration.HttpConfiguration http = config == null ? null : config.getHttp();

        return new WaitConfiguration.Builder()
                .time(valueProvider.getInt(WAIT_TIME, config == null ? null : config.getTime()))
                .healthy(valueProvider.getBoolean(WAIT_HEALTHY, config == null ? null : config.getHealthy()))
                .url(url)
                .preStop(valueProvider.getString(WAIT_EXEC_PRE_STOP, exec == null ? null : exec.getPreStop()))
                .postStart(valueProvider.getString(WAIT_EXEC_POST_START, exec == null ? null : exec.getPostStart()))
                .breakOnError(valueProvider.getBoolean(WAIT_EXEC_BREAK_ON_ERROR, exec == null ? null : exec.isBreakOnError()))
                .method(valueProvider.getString(WAIT_HTTP_METHOD, http == null ? null : http.getMethod()))
                .status(valueProvider.getString(WAIT_HTTP_STATUS, http == null ? null : http.getStatus()))
                .log(valueProvider.getString(WAIT_LOG, config == null ? null : config.getLog()))
                .kill(valueProvider.getInteger(WAIT_KILL, config == null ? null : config.getKill()))
                .exit(valueProvider.getInteger(WAIT_EXIT, config == null ? null : config.getExit()))
                .shutdown(valueProvider.getInteger(WAIT_SHUTDOWN, config == null ? null : config.getShutdown()))
                .tcpHost(valueProvider.getString(WAIT_TCP_HOST, tcp == null ? null : tcp.getHost()))
                .tcpPorts(valueProvider.getIntList(WAIT_TCP_PORT, tcp == null ? null : tcp.getPorts()))
                .tcpMode(valueProvider.getString(WAIT_TCP_MODE, tcp == null || tcp.getMode() == null ? null : tcp.getMode().name()))
                .build();
    }

    private WatchImageConfiguration extractWatchConfig(ImageConfiguration fromConfig, ValueProvider valueProvider) {
        WatchImageConfiguration config = fromConfig.getWatchConfiguration();

        return new WatchImageConfiguration.Builder()
                .interval(valueProvider.getInteger(WATCH_INTERVAL, config == null ? null : config.getIntervalRaw()))
                .postGoal(valueProvider.getString(WATCH_POSTGOAL, config == null ? null : config.getPostGoal()))
                .postExec(valueProvider.getString(WATCH_POSTEXEC, config == null ? null : config.getPostExec()))
                .mode(valueProvider.getString(WATCH_POSTGOAL, config == null || config.getMode() == null ? null : config.getMode().name()))
                .build();
    }

    private List<UlimitConfig> extractUlimits(List<UlimitConfig> config, ValueProvider valueProvider) {
        List<String> other = null;
        if (config != null) {
            other = new ArrayList<>();
            // Convert back to string for potential merge
            for (UlimitConfig ulimitConfig : config) {
                other.add(ulimitConfig.serialize());
            }
        }

        List<String> ulimits = valueProvider.getList(ConfigKey.ULIMITS, other);
        if (ulimits == null) {
            return null;
        }
        List<UlimitConfig> ret = new ArrayList<>();
        for (String ulimit : ulimits) {
            ret.add(new UlimitConfig(ulimit));
        }
        return ret;
    }

    private RunVolumeConfiguration extractVolumeConfig(RunVolumeConfiguration config, ValueProvider valueProvider) {
        return new RunVolumeConfiguration.Builder()
                .bind(valueProvider.getList(BIND, config == null ? null : config.getBind()))
                .from(valueProvider.getList(VOLUMES_FROM, config == null ? null : config.getFrom()))
                .build();
    }

    private static String getPrefix(Map<String, String> externalConfig) {
        String prefix = externalConfig.get("prefix");
        if (prefix == null) {
            prefix = DEFAULT_PREFIX;
        }
        return prefix;
    }

    private static PropertyMode getMode(Map<String, String> externalConfig) {
        return PropertyMode.parse(externalConfig.get("mode"));
    }

    public static boolean canCoexistWithOtherPropertyConfiguredImages(Map<String, String> externalConfig) {
        if(externalConfig == null || externalConfig.isEmpty()) {
            return false;
        }

        if(!TYPE_NAME.equals(externalConfig.get("type")))
        {
            // This images loads config from something totally different
            return true;
        }

        if(externalConfig.get("prefix") != null)
        {
            // This image has a specified prefix. If multiple images have explicitly set docker. as prefix we
            // assume user know what they are doing and allow it.
            return true;
        }

        return false;
    }
}
