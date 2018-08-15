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

import java.util.*;

import io.fabric8.maven.docker.config.*;
import io.fabric8.maven.docker.config.handler.ExternalConfigHandler;
import io.fabric8.maven.docker.util.EnvUtil;
import com.google.common.base.Function;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.CollectionUtils;

import javax.annotation.Nullable;

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
        BuildImageConfiguration build = extractBuildConfiguration(fromConfig, valueProvider);
        WatchImageConfiguration watch = extractWatchConfig(fromConfig, valueProvider);
        String name = valueProvider.optString(NAME, fromConfig.getName());
        String alias = valueProvider.optString(ALIAS, fromConfig.getAlias());

        if (name == null) {
            throw new IllegalArgumentException(String.format("Mandatory property [%s] is not defined", NAME));
        }

        return Collections.singletonList(
                new ImageConfiguration.Builder()
                        .name(name)
                        .alias(alias)
                        .runConfig(run)
                        .buildConfig(build)
                        .watchConfig(watch)
                        .build());
    }

    // Enable build config only when a `.from.`, `.dockerFile.`, or `.dockerFileDir.` is configured
    private boolean buildConfigured(BuildImageConfiguration config, ValueProvider valueProvider) {
        return valueProvider.optString(FROM, config == null ? null : config.getFrom()) != null ||
                valueProvider.getMap(FROM_EXT, config == null ? null : config.getFromExt()) != null ||
                valueProvider.optString(DOCKER_FILE, config == null || config.getDockerFileRaw() == null ? null : config.getDockerFileRaw()) != null ||
                valueProvider.optString(DOCKER_FILE_DIR, config == null || config.getDockerArchiveRaw() == null ? null : config.getDockerArchiveRaw()) != null;
    }


    private BuildImageConfiguration extractBuildConfiguration(ImageConfiguration fromConfig, ValueProvider valueProvider) {
        BuildImageConfiguration config = fromConfig.getBuildConfiguration();
        if (!buildConfigured(config, valueProvider)) {
            return null;
        }

        return new BuildImageConfiguration.Builder()
                .cmd(extractArguments(valueProvider, CMD, config == null ? null : config.getCmd()))
                .cleanup(valueProvider.optString(CLEANUP, config == null ? null : config.getCleanup()))
                .nocache(valueProvider.optBoolean(NOCACHE, config == null ? null : config.getNoCache()))
                .optimise(valueProvider.optBoolean(OPTIMISE, config == null ? null : config.getOptimise()))
                .entryPoint(extractArguments(valueProvider, ENTRYPOINT, config == null ? null : config.getEntryPoint()))
                .assembly(extractAssembly(config == null ? null : config.getAssemblyConfiguration(), valueProvider))
                .env(CollectionUtils.mergeMaps(
                        valueProvider.getMap(ENV_BUILD, config == null ? null : config.getEnv()),
                        valueProvider.getMap(ENV, Collections.<String, String>emptyMap())
                ))
                .args(valueProvider.getMap(ARGS, config == null ? null : config.getArgs()))
                .labels(valueProvider.getMap(LABELS, config == null ? null : config.getLabels()))
                .ports(extractPortValues(config == null ? null : config.getPorts(), valueProvider))
                .runCmds(valueProvider.getList(RUN, config == null ? null : config.getRunCmds()))
                .from(valueProvider.optString(FROM, config == null ? null : config.getFrom()))
                .fromExt(valueProvider.getMap(FROM_EXT, config == null ? null : config.getFromExt()))
                .registry(valueProvider.optString(REGISTRY, config == null ? null : config.getRegistry()))
                .volumes(valueProvider.getList(VOLUMES, config == null ? null : config.getVolumes()))
                .tags(valueProvider.getList(TAGS, config == null ? null : config.getTags()))
                .maintainer(valueProvider.optString(MAINTAINER, config == null ? null : config.getMaintainer()))
                .workdir(valueProvider.optString(WORKDIR, config == null ? null : config.getWorkdir()))
                .skip(valueProvider.optBoolean(SKIP_BUILD, config == null ? null : config.getSkip()))
                .imagePullPolicy(valueProvider.optString(IMAGE_PULL_POLICY_BUILD, config == null ? null : config.getImagePullPolicy()))
                .dockerArchive(valueProvider.optString(DOCKER_ARCHIVE, config == null ? null : config.getDockerArchiveRaw()))
                .dockerFile(valueProvider.optString(DOCKER_FILE, config == null ? null : config.getDockerFileRaw()))
                .dockerFileDir(valueProvider.optString(DOCKER_FILE_DIR, config == null ? null : config.getDockerFileDirRaw()))
                .buildOptions(valueProvider.getMap(BUILD_OPTIONS, config == null ? null : config.getBuildOptions()))
                .filter(valueProvider.optString(FILTER, config == null ? null : config.getFilterRaw()))
                .user(valueProvider.optString(USER, config == null ? null : config.getUser()))
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
                .net(valueProvider.optString(NET, config == null ? null : config.getNetRaw()))
                .network(extractNetworkConfig(config == null ? null : config.getNetworkingConfig(), valueProvider))
                .dnsSearch(valueProvider.getList(DNS_SEARCH, config == null ? null : config.getDnsSearch()))
                .domainname(valueProvider.optString(DOMAINNAME, config == null ? null : config.getDomainname()))
                .entrypoint(extractArguments(valueProvider, ENTRYPOINT, config == null ? null : config.getEntrypoint()))
                .env(CollectionUtils.mergeMaps(
                        valueProvider.getMap(ENV_RUN, config == null ? null : config.getEnv()),
                        valueProvider.getMap(ENV, Collections.<String, String>emptyMap())
                ))
                .labels(valueProvider.getMap(LABELS, config == null ? null : config.getLabels()))
                .envPropertyFile(valueProvider.optString(ENV_PROPERTY_FILE, config == null ? null : config.getEnvPropertyFile()))
                .extraHosts(valueProvider.getList(EXTRA_HOSTS, config == null ? null : config.getExtraHosts()))
                .hostname(valueProvider.optString(HOSTNAME, config == null ? null : config.getHostname()))
                .links(valueProvider.getList(LINKS, config == null ? null : config.getLinks()))
                .memory(valueProvider.getLong(MEMORY, config == null ? null : config.getMemory()))
                .memorySwap(valueProvider.getLong(MEMORY_SWAP, config == null ? null : config.getMemorySwap()))
                .namingStrategy(valueProvider.optString(NAMING_STRATEGY, config == null || config.getNamingStrategyRaw() == null ? null : config.getNamingStrategyRaw().name()))
                .exposedPropertyKey(valueProvider.optString(EXPOSED_PROPERTY_KEY, config == null ? null : config.getExposedPropertyKey()))
                .portPropertyFile(valueProvider.optString(PORT_PROPERTY_FILE, config == null ? null : config.getPortPropertyFile()))
                .ports(valueProvider.getList(PORTS, config == null ? null : config.getPorts()))
                .shmSize(valueProvider.getLong(SHMSIZE, config == null ? null : config.getShmSize()))
                .privileged(valueProvider.optBoolean(PRIVILEGED, config == null ? null : config.getPrivileged()))
                .restartPolicy(extractRestartPolicy(config == null ? null : config.getRestartPolicy(), valueProvider))
                .user(valueProvider.optString(USER, config == null ? null : config.getUser()))
                .workingDir(valueProvider.optString(WORKING_DIR, config == null ? null : config.getWorkingDir()))
                .log(extractLogConfig(config == null ? null : config.getLogConfiguration(), valueProvider))
                .wait(extractWaitConfig(config == null ? null : config.getWaitConfiguration(), valueProvider))
                .volumes(extractVolumeConfig(config == null ? null : config.getVolumeConfiguration(), valueProvider))
                .skip(valueProvider.optBoolean(SKIP_RUN, config == null ? null : config.getSkip()))
                .imagePullPolicy(valueProvider.optString(IMAGE_PULL_POLICY_RUN, config == null ? null : config.getImagePullPolicy()))
                .ulimits(extractUlimits(config == null ? null : config.getUlimits(), valueProvider))
                .tmpfs(valueProvider.getList(TMPFS, config == null ? null : config.getTmpfs()))
                .build();
    }

    private NetworkConfig extractNetworkConfig(NetworkConfig config, ValueProvider valueProvider) {
        return new NetworkConfig.Builder()
            .mode(valueProvider.optString(NETWORK_MODE, config == null || config.getMode() == null ? null : config.getMode().name()))
            .name(valueProvider.optString(NETWORK_NAME, config == null ? null : config.getName()))
            .aliases(valueProvider.getList(NETWORK_ALIAS, config == null ? null : config.getAliases()))
            .build();
    }

    @SuppressWarnings("deprecation")
    private AssemblyConfiguration extractAssembly(AssemblyConfiguration config, ValueProvider valueProvider) {
        return new AssemblyConfiguration.Builder()
                .targetDir(valueProvider.optString(ASSEMBLY_BASEDIR, config == null ? null : config.getTargetDir()))
                .descriptor(valueProvider.optString(ASSEMBLY_DESCRIPTOR, config == null ? null : config.getDescriptor()))
                .descriptorRef(valueProvider.optString(ASSEMBLY_DESCRIPTOR_REF, config == null ? null : config.getDescriptorRef()))
                .dockerFileDir(valueProvider.optString(ASSEMBLY_DOCKER_FILE_DIR, config == null ? null : config.getDockerFileDir()))
                .exportBasedir(valueProvider.optBoolean(ASSEMBLY_EXPORT_BASEDIR, config == null ? null : config.getExportTargetDir()))
                .ignorePermissions(valueProvider.optBoolean(ASSEMBLY_IGNORE_PERMISSIONS, config == null ? null : config.getIgnorePermissions()))
                .permissions(valueProvider.optString(ASSEMBLY_PERMISSIONS, config == null ? null : config.getPermissionsRaw()))
                .user(valueProvider.optString(ASSEMBLY_USER, config == null ? null : config.getUser()))
                .mode(valueProvider.optString(ASSEMBLY_MODE, config == null ? null : config.getModeRaw()))
                .tarLongFileMode(valueProvider.optString(ASSEMBLY_TARLONGFILEMODE, config == null ? null : config.getTarLongFileMode()))
                .build();
    }

    private HealthCheckConfiguration extractHealthCheck(HealthCheckConfiguration config, ValueProvider valueProvider) {
        Map<String, String> healthCheckProperties = valueProvider.getMap(HEALTHCHECK, Collections.<String, String>emptyMap());
        if (healthCheckProperties != null && healthCheckProperties.size() > 0) {
            return new HealthCheckConfiguration.Builder()
                    .interval(valueProvider.optString(HEALTHCHECK_INTERVAL, config == null ? null : config.getInterval()))
                    .timeout(valueProvider.optString(HEALTHCHECK_TIMEOUT, config == null ? null : config.getTimeout()))
                    .startPeriod(valueProvider.optString(HEALTHCHECK_START_PERIOD, config == null ? null : config.getStartPeriod()))
                    .retries(valueProvider.getInteger(HEALTHCHECK_RETRIES, config == null ? null : config.getRetries()))
                    .mode(valueProvider.optString(HEALTHCHECK_MODE, config == null || config.getMode() == null ? null : config.getMode().name()))
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
        return valueProvider.getObject(configKey, alternative, new Function<String, Arguments>() {
            @Override
            public Arguments apply(@Nullable String raw) {
                return raw != null ? new Arguments(raw) : null;
            }
        });
    }

    private RestartPolicy extractRestartPolicy(RestartPolicy config, ValueProvider valueProvider) {
        return new RestartPolicy.Builder()
                .name(valueProvider.optString(RESTART_POLICY_NAME, config == null ? null : config.getName()))
                .retry(valueProvider.optInt(RESTART_POLICY_RETRY, config == null || config.getRetry() == 0 ? null : config.getRetry()))
                .build();
    }

    private LogConfiguration extractLogConfig(LogConfiguration config, ValueProvider valueProvider) {
        LogConfiguration.Builder builder = new LogConfiguration.Builder()
            .color(valueProvider.optString(LOG_COLOR, config == null ? null : config.getColor()))
            .date(valueProvider.optString(LOG_DATE, config == null ? null : config.getDate()))
            .file(valueProvider.optString(LOG_FILE, config == null ? null : config.getFileLocation()))
            .prefix(valueProvider.optString(LOG_PREFIX, config == null ? null : config.getPrefix()))
            .logDriverName(valueProvider.optString(LOG_DRIVER_NAME, config == null || config.getDriver() == null ? null : config.getDriver().getName()))
            .logDriverOpts(valueProvider.getMap(LOG_DRIVER_OPTS, config == null || config.getDriver() == null ? null : config.getDriver().getOpts()));

        Boolean configEnabled = config != null ? config.isEnabled() : null;
        Boolean enabled = valueProvider.optBoolean(LOG_ENABLED, configEnabled);
        builder.enabled(enabled);
        return builder.build();
    }

    private WaitConfiguration extractWaitConfig(WaitConfiguration config, ValueProvider valueProvider) {
        String url = valueProvider.optString(WAIT_HTTP_URL, config == null ? null : config.getUrl());
        if (url == null) {
            // Fallback to deprecated old URL
            url = valueProvider.optString(WAIT_URL, config == null ? null : config.getUrl());
        }
        WaitConfiguration.ExecConfiguration exec = config == null ? null : config.getExec();
        WaitConfiguration.TcpConfiguration tcp = config == null ? null : config.getTcp();
        WaitConfiguration.HttpConfiguration http = config == null ? null : config.getHttp();

        return new WaitConfiguration.Builder()
                .time(valueProvider.optInt(WAIT_TIME, config == null ? null : config.getTime()))
                .healthy(valueProvider.optBoolean(WAIT_HEALTHY, config == null ? null : config.getHealthy()))
                .url(url)
                .preStop(valueProvider.optString(WAIT_EXEC_PRE_STOP, exec == null ? null : exec.getPreStop()))
                .postStart(valueProvider.optString(WAIT_EXEC_POST_START, exec == null ? null : exec.getPostStart()))
                .breakOnError(valueProvider.optBoolean(WAIT_EXEC_BREAK_ON_ERROR, exec == null ? null : exec.isBreakOnError()))
                .method(valueProvider.optString(WAIT_HTTP_METHOD, http == null ? null : http.getMethod()))
                .status(valueProvider.optString(WAIT_HTTP_STATUS, http == null ? null : http.getStatus()))
                .log(valueProvider.optString(WAIT_LOG, config == null ? null : config.getLog()))
                .kill(valueProvider.getInteger(WAIT_KILL, config == null ? null : config.getKill()))
                .exit(valueProvider.getInteger(WAIT_EXIT, config == null ? null : config.getExit()))
                .shutdown(valueProvider.getInteger(WAIT_SHUTDOWN, config == null ? null : config.getShutdown()))
                .tcpHost(valueProvider.optString(WAIT_TCP_HOST, tcp == null ? null : tcp.getHost()))
                .tcpPorts(valueProvider.getIntList(WAIT_TCP_PORT, tcp == null ? null : tcp.getPorts()))
                .tcpMode(valueProvider.optString(WAIT_TCP_MODE, tcp == null || tcp.getMode() == null ? null : tcp.getMode().name()))
                .build();
    }

    private WatchImageConfiguration extractWatchConfig(ImageConfiguration fromConfig, ValueProvider valueProvider) {
        WatchImageConfiguration config = fromConfig.getWatchConfiguration();

        return new WatchImageConfiguration.Builder()
                .interval(valueProvider.getInteger(WATCH_INTERVAL, config == null ? null : config.getIntervalRaw()))
                .postGoal(valueProvider.optString(WATCH_POSTGOAL, config == null ? null : config.getPostGoal()))
                .postExec(valueProvider.optString(WATCH_POSTEXEC, config == null ? null : config.getPostExec()))
                .mode(valueProvider.optString(WATCH_POSTGOAL, config == null || config.getMode() == null ? null : config.getMode().name()))
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
