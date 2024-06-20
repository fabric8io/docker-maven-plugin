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

import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.AttestationConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.BuildXConfiguration;
import io.fabric8.maven.docker.config.CopyConfiguration;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ALIAS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ARGS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ASSEMBLIES;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ASSEMBLY;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ASSEMBLY_BASEDIR;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ASSEMBLY_DESCRIPTOR;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ASSEMBLY_DESCRIPTOR_REF;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ASSEMBLY_DOCKER_FILE_DIR;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ASSEMBLY_EXPORT_BASEDIR;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ASSEMBLY_IGNORE_PERMISSIONS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ASSEMBLY_MODE;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ASSEMBLY_NAME;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ASSEMBLY_PERMISSIONS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ASSEMBLY_TARLONGFILEMODE;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ASSEMBLY_USER;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.AUTO_REMOVE;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.BIND;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.BUILDX_ATTESTATION_PROVENANCE;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.BUILDX_ATTESTATION_SBOM;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.BUILDX_BUILDERNAME;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.BUILDX_CACHE_FROM;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.BUILDX_CACHE_TO;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.BUILDX_CONFIGFILE;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.BUILDX_DOCKERSTATEDIR;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.BUILDX_NODENAME;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.BUILDX_PLATFORMS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.BUILD_NETWORK;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.BUILD_OPTIONS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.CACHE_FROM;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.CAP_ADD;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.CAP_DROP;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.CLEANUP;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.CMD;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.CONTEXT_DIR;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.COPY_ENTRIES;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.COPY_NAME_PATTERN;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.CPUS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.CPUSET;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.CPUSHARES;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.DEPENDS_ON;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.DNS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.DNS_SEARCH;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.DOCKER_ARCHIVE;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.DOCKER_FILE;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.DOCKER_FILE_DIR;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.DOMAINNAME;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ENTRYPOINT;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ENV;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ENV_BUILD;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ENV_PROPERTY_FILE;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ENV_RUN;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.EXPOSED_PROPERTY_KEY;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.EXTRA_HOSTS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.FILTER;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.FROM;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.FROM_EXT;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.HEALTHCHECK;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.HEALTHCHECK_CMD;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.HEALTHCHECK_INTERVAL;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.HEALTHCHECK_MODE;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.HEALTHCHECK_RETRIES;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.HEALTHCHECK_START_PERIOD;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.HEALTHCHECK_TIMEOUT;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.HOSTNAME;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.IMAGE_PULL_POLICY_BUILD;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.IMAGE_PULL_POLICY_RUN;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.ISOLATION;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.LABELS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.LINKS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.LOAD_NAME_PATTERN;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.LOG_COLOR;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.LOG_DATE;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.LOG_DRIVER_NAME;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.LOG_DRIVER_OPTS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.LOG_ENABLED;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.LOG_FILE;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.LOG_PREFIX;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.MAINTAINER;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.MEMORY;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.MEMORY_SWAP;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.NAME;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.NAMING_STRATEGY;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.NET;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.NETWORK_ALIAS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.NETWORK_MODE;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.NETWORK_NAME;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.NO_CACHE;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.OPTIMISE;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.PLATFORM;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.PORTS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.PORT_PROPERTY_FILE;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.PRIVILEGED;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.READ_ONLY;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.REGISTRY;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.REMOVE_NAME_PATTERN;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.RESTART_POLICY_NAME;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.RESTART_POLICY_RETRY;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.RUN;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.SECURITY_OPTS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.SHELL;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.SHMSIZE;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.SKIP_BUILD;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.SKIP_PUSH;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.SKIP_RUN;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.SQUASH;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.STOP_NAME_PATTERN;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.SYSCTLS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.TAGS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.TMPFS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.USER;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.USE_DEFAULT_EXCLUDES;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.VOLUMES;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.VOLUMES_FROM;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WAIT_EXEC_BREAK_ON_ERROR;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WAIT_EXEC_POST_START;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WAIT_EXEC_PRE_STOP;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WAIT_EXIT;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WAIT_HEALTHY;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WAIT_HTTP_METHOD;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WAIT_HTTP_STATUS;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WAIT_HTTP_URL;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WAIT_KILL;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WAIT_LOG;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WAIT_SHUTDOWN;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WAIT_TCP_HOST;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WAIT_TCP_MODE;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WAIT_TCP_PORT;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WAIT_TIME;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WAIT_URL;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WATCH_INTERVAL;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WATCH_POSTEXEC;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WATCH_POSTGOAL;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WORKDIR;
import static io.fabric8.maven.docker.config.handler.property.ConfigKey.WORKING_DIR;

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
        CopyConfiguration copyConfig = extractCopyConfig(fromConfig, valueProvider);
        String name = valueProvider.getString(NAME, fromConfig.getName());
        String alias = valueProvider.getString(ALIAS, fromConfig.getAlias());
        String removeNamePattern = valueProvider.getString(REMOVE_NAME_PATTERN, fromConfig.getRemoveNamePattern());
        String copyNamePattern = valueProvider.getString(COPY_NAME_PATTERN, fromConfig.getCopyNamePattern());
        String stopNamePattern = valueProvider.getString(STOP_NAME_PATTERN, fromConfig.getStopNamePattern());

        if (name == null) {
            throw new IllegalArgumentException(String.format("Mandatory property [%s] is not defined", NAME));
        }

        return Collections.singletonList(
            new ImageConfiguration.Builder()
                .name(name)
                .alias(alias)
                .removeNamePattern(removeNamePattern)
                .copyNamePattern(copyNamePattern)
                .stopNamePattern(stopNamePattern)
                .runConfig(run)
                .buildConfig(build)
                .watchConfig(watch)
                .copyConfig(copyConfig)
                .build());
    }

    private boolean isStringValueNull(ValueProvider valueProvider, ConfigKey key, Supplier<String> supplier) {
        return valueProvider.getString(key, supplier.get()) != null;
    }

    // Enable build config only when a `.from.`, `.dockerFile.`, or `.dockerFileDir.` is configured
    private boolean buildConfigured(BuildImageConfiguration config, ValueProvider valueProvider, MavenProject project) {

        if (isStringValueNull(valueProvider, FROM, config::getFrom)) {
            return true;
        }

        if (valueProvider.getMap(FROM_EXT, config.getFromExt()) != null) {
            return true;
        }

        if (isStringValueNull(valueProvider, DOCKER_FILE, config::getDockerFileRaw)) {
            return true;
        }

        if (isStringValueNull(valueProvider, DOCKER_ARCHIVE, config::getDockerArchiveRaw)) {
            return true;
        }

        if (isStringValueNull(valueProvider, CONTEXT_DIR, config::getContextDirRaw)) {
            return true;
        }

        if (isStringValueNull(valueProvider, DOCKER_FILE_DIR, config::getDockerFileDirRaw)) {
            return true;
        }

        // Simple Dockerfile mode
        return new File(project.getBasedir(), "Dockerfile").exists();
    }

    @SuppressWarnings("deprecation")
    private BuildImageConfiguration extractBuildConfiguration(ImageConfiguration fromConfig, ValueProvider valueProvider, MavenProject project) {
        BuildImageConfiguration config = fromConfig.getBuildConfiguration();
        if (config == null) {
            config = new BuildImageConfiguration();
        }
        if (!buildConfigured(config, valueProvider, project)) {
            return null;
        }

        BuildImageConfiguration.Builder builder = new BuildImageConfiguration.Builder();
        builder.cmd(extractArguments(valueProvider, CMD, config.getCmd()))
            .cleanup(valueProvider.getString(CLEANUP, config.getCleanup()))
            .noCache(valueProvider.getBoolean(NO_CACHE, config.getNoCache()))
            .squash(valueProvider.getBoolean(SQUASH, config.getSquash()))
            .cacheFrom(valueProvider.getList(CACHE_FROM, config.getCacheFrom()))
            .optimise(valueProvider.getBoolean(OPTIMISE, config.getOptimise()))
            .entryPoint(extractArguments(valueProvider, ENTRYPOINT, config.getEntryPoint()))
            .assembly(extractAssembly(config.getAssemblyConfiguration(), valueProvider))
            .assemblies(extractAssemblies(config.getAssembliesConfiguration(), valueProvider))
            .env(CollectionUtils.mergeMaps(
                valueProvider.getMap(ENV_BUILD, config.getEnv()),
                valueProvider.getMap(ENV, Collections.emptyMap())
            ))
            .args(valueProvider.getMap(ARGS, config.getArgs()))
            .labels(valueProvider.getMap(LABELS, config.getLabels()))
            .ports(extractPortValues(config.getPorts(), valueProvider))
            .shell(extractArguments(valueProvider, SHELL, config.getShell()))
            .runCmds(valueProvider.getRunCommands(RUN, config.getRunCmds()))
            .from(valueProvider.getString(FROM, config.getFrom()))
            .fromExt(valueProvider.getMap(FROM_EXT, config.getFromExt()))
            .registry(valueProvider.getString(REGISTRY, config.getRegistry()))
            .volumes(valueProvider.getList(VOLUMES, config.getVolumes()))
            .tags(valueProvider.getList(TAGS, config.getTags()))
            .maintainer(valueProvider.getString(MAINTAINER, config.getMaintainer()))
            .network(valueProvider.getString(BUILD_NETWORK, config.getNetwork()))
            .workdir(valueProvider.getString(WORKDIR, config.getWorkdir()))
            .skip(valueProvider.getBoolean(SKIP_BUILD, config.getSkip()))
            .skipPush(valueProvider.getBoolean(SKIP_PUSH, config.getSkipPush()))
            .skipTag(valueProvider.getBoolean(SKIP_TAG, config.skipTag()))
            .imagePullPolicy(valueProvider.getString(IMAGE_PULL_POLICY_BUILD, config.getImagePullPolicy()))
            .contextDir(valueProvider.getString(CONTEXT_DIR, config.getContextDirRaw()))
            .dockerArchive(valueProvider.getString(DOCKER_ARCHIVE, config.getDockerArchiveRaw()))
            .loadNamePattern(valueProvider.getString(LOAD_NAME_PATTERN, config.getLoadNamePattern()))
            .dockerFile(valueProvider.getString(DOCKER_FILE, config.getDockerFileRaw()))
            .dockerFileDir(valueProvider.getString(DOCKER_FILE_DIR, config.getDockerFileDirRaw()))
            .buildOptions(valueProvider.getMap(BUILD_OPTIONS, config.getBuildOptions()))
            .useDefaultExcludes(valueProvider.getBoolean(USE_DEFAULT_EXCLUDES, config.getUseDefaultExcludes()))
            .filter(valueProvider.getString(FILTER, config.getFilterRaw()))
            .user(valueProvider.getString(USER, config.getUser()))
            .healthCheck(extractHealthCheck(config.getHealthCheck(), valueProvider))
            .buildx(extractBuildx(config.getBuildX(), valueProvider));
        return builder.build();
    }

    private RunImageConfiguration extractRunConfiguration(ImageConfiguration fromConfig, ValueProvider valueProvider) {
        RunImageConfiguration config = fromConfig.getRunConfiguration();
        if (config == null) {
            config = new RunImageConfiguration();
        }

        return new RunImageConfiguration.Builder()
            .capAdd(valueProvider.getList(CAP_ADD, config.getCapAdd()))
            .capDrop(valueProvider.getList(CAP_DROP, config.getCapDrop()))
            .sysctls(valueProvider.getMap(SYSCTLS, config.getSysctls()))
            .securityOpts(valueProvider.getList(SECURITY_OPTS, config.getSecurityOpts()))
            .cmd(extractArguments(valueProvider, CMD, config.getCmd()))
            .dns(valueProvider.getList(DNS, config.getDns()))
            .dependsOn(valueProvider.getList(DEPENDS_ON, config.getDependsOn()))
            .net(valueProvider.getString(NET, config.getNetRaw()))
            .network(extractNetworkConfig(config.getNetworkingConfig(), valueProvider))
            .dnsSearch(valueProvider.getList(DNS_SEARCH, config.getDnsSearch()))
            .domainname(valueProvider.getString(DOMAINNAME, config.getDomainname()))
            .entrypoint(extractArguments(valueProvider, ENTRYPOINT, config.getEntrypoint()))
            .env(CollectionUtils.mergeMaps(
                valueProvider.getMap(ENV_RUN, config.getEnv()),
                valueProvider.getMap(ENV, Collections.emptyMap())
            ))
            .labels(valueProvider.getMap(LABELS, config.getLabels()))
            .envPropertyFile(valueProvider.getString(ENV_PROPERTY_FILE, config.getEnvPropertyFile()))
            .extraHosts(valueProvider.getList(EXTRA_HOSTS, config.getExtraHosts()))
            .hostname(valueProvider.getString(HOSTNAME, config.getHostname()))
            .links(valueProvider.getList(LINKS, config.getLinks()))
            .memory(valueProvider.getLong(MEMORY, config.getMemory()))
            .memorySwap(valueProvider.getLong(MEMORY_SWAP, config.getMemorySwap()))
            .namingStrategy(valueProvider.getString(NAMING_STRATEGY, config.getNamingStrategy() == null ? null : config.getNamingStrategy().name()))
            .exposedPropertyKey(valueProvider.getString(EXPOSED_PROPERTY_KEY, config.getExposedPropertyKey()))
            .portPropertyFile(valueProvider.getString(PORT_PROPERTY_FILE, config.getPortPropertyFile()))
            .ports(valueProvider.getList(PORTS, config.getPorts()))
            .shmSize(valueProvider.getLong(SHMSIZE, config.getShmSize()))
            .privileged(valueProvider.getBoolean(PRIVILEGED, config.getPrivileged()))
            .restartPolicy(extractRestartPolicy(config.getRestartPolicy(), valueProvider))
            .user(valueProvider.getString(USER, config.getUser()))
            .workingDir(valueProvider.getString(WORKING_DIR, config.getWorkingDir()))
            .log(extractLogConfig(config.getLogConfiguration(), valueProvider))
            .wait(extractWaitConfig(config.getWaitConfiguration(), valueProvider))
            .volumes(extractVolumeConfig(config.getVolumeConfiguration(), valueProvider))
            .skip(valueProvider.getBoolean(SKIP_RUN, config.getSkip()))
            .imagePullPolicy(valueProvider.getString(IMAGE_PULL_POLICY_RUN, config.getImagePullPolicy()))
            .platform(valueProvider.getString(PLATFORM, config.getPlatform()))
            .ulimits(extractUlimits(config.getUlimits(), valueProvider))
            .tmpfs(valueProvider.getList(TMPFS, config.getTmpfs()))
            .isolation(valueProvider.getString(ISOLATION, config.getIsolation()))
            .cpuShares(valueProvider.getLong(CPUSHARES, config.getCpuShares()))
            .cpus(valueProvider.getDouble(CPUS, config.getCpus()))
            .cpuSet(valueProvider.getString(CPUSET, config.getCpuSet()))
            .readOnly(valueProvider.getBoolean(READ_ONLY, config.getReadOnly()))
            .autoRemove(valueProvider.getBoolean(AUTO_REMOVE, config.getAutoRemove()))
            .build();
    }

    private NetworkConfig extractNetworkConfig(NetworkConfig config, ValueProvider valueProvider) {
        if (config == null) {
            config = new NetworkConfig();
        }

        return new NetworkConfig.Builder()
            .mode(valueProvider.getString(NETWORK_MODE, config.getMode() == null ? null : config.getMode().name()))
            .name(valueProvider.getString(NETWORK_NAME, config.getName()))
            .aliases(valueProvider.getList(NETWORK_ALIAS, config.getAliases()))
            .build();
    }

    private List<AssemblyConfiguration> extractAssemblies(List<AssemblyConfiguration> config, ValueProvider valueProvider) {
        List<ValueProvider> assemblyConfigProviders = valueProvider.getNestedList(ASSEMBLIES);
        List<AssemblyConfiguration> assemblies = new ArrayList<>();

        int count = Math.max(assemblyConfigProviders.size(), config == null ? 0 : config.size());

        for (int i = 0; i < count; i++) {
            AssemblyConfiguration fromConfig = config == null || i >= config.size() ? null : config.get(i);

            if (i >= assemblyConfigProviders.size()) {
                assemblies.add(fromConfig);
            } else {
                ValueProvider provider = assemblyConfigProviders.get(i);
                assemblies.add(extractAssembly(fromConfig, provider));
            }
        }

        return assemblies;
    }

    @SuppressWarnings("deprecation")
    private AssemblyConfiguration extractAssembly(AssemblyConfiguration config, ValueProvider valueProvider) {
        Map<String, String> assemblyProperties = valueProvider.getMap(ASSEMBLY, Collections.emptyMap());

        if (assemblyProperties == null || assemblyProperties.isEmpty()) {
            return config;
        }

        if (config == null) {
            config = new AssemblyConfiguration();
        }

        AssemblyConfiguration.Builder builder = new AssemblyConfiguration.Builder()
            .targetDir(valueProvider.getString(ASSEMBLY_BASEDIR, config.getTargetDir()))
            .descriptor(valueProvider.getString(ASSEMBLY_DESCRIPTOR, config.getDescriptor()))
            .descriptorRef(valueProvider.getString(ASSEMBLY_DESCRIPTOR_REF, config.getDescriptorRef()))
            .dockerFileDir(valueProvider.getString(ASSEMBLY_DOCKER_FILE_DIR, config.getDockerFileDir()))
            .exportBasedir(valueProvider.getBoolean(ASSEMBLY_EXPORT_BASEDIR, config.getExportTargetDir()))
            .ignorePermissions(valueProvider.getBoolean(ASSEMBLY_IGNORE_PERMISSIONS, config.getIgnorePermissions()))
            .permissions(valueProvider.getString(ASSEMBLY_PERMISSIONS, config.getPermissionsRaw()))
            .user(valueProvider.getString(ASSEMBLY_USER, config.getUser()))
            .mode(valueProvider.getString(ASSEMBLY_MODE, config.getModeRaw()))
            .assemblyDef(config.getInline())
            .tarLongFileMode(valueProvider.getString(ASSEMBLY_TARLONGFILEMODE, config.getTarLongFileMode()));
        String name = valueProvider.getString(ASSEMBLY_NAME, config.getName());
        if (name != null) {
            builder.name(name);
        }
        return builder.build();
    }

    private HealthCheckConfiguration extractHealthCheck(HealthCheckConfiguration config, ValueProvider valueProvider) {
        Map<String, String> healthCheckProperties = valueProvider.getMap(HEALTHCHECK, Collections.emptyMap());
        if (healthCheckProperties == null || healthCheckProperties.isEmpty()) {
            return config;
        }
        if (config == null) {
            config = new HealthCheckConfiguration();
        }

        return new HealthCheckConfiguration.Builder()
            .interval(valueProvider.getString(HEALTHCHECK_INTERVAL, config.getInterval()))
            .timeout(valueProvider.getString(HEALTHCHECK_TIMEOUT, config.getTimeout()))
            .startPeriod(valueProvider.getString(HEALTHCHECK_START_PERIOD, config.getStartPeriod()))
            .retries(valueProvider.getInteger(HEALTHCHECK_RETRIES, config.getRetries()))
            .mode(valueProvider.getString(HEALTHCHECK_MODE, config.getMode() == null ? null : config.getMode().name()))
            .cmd(extractArguments(valueProvider, HEALTHCHECK_CMD, config.getCmd()))
            .build();
    }

    private BuildXConfiguration extractBuildx(BuildXConfiguration config, ValueProvider valueProvider) {
        if (config == null) {
            config = new BuildXConfiguration();
        }

        return new BuildXConfiguration.Builder()
            .builderName(valueProvider.getString(BUILDX_BUILDERNAME, config.getBuilderName()))
            .nodeName(valueProvider.getString(BUILDX_NODENAME, config.getNodeName()))
            .configFile(valueProvider.getString(BUILDX_CONFIGFILE, config.getConfigFile()))
            .dockerStateDir(valueProvider.getString(BUILDX_DOCKERSTATEDIR, config.getDockerStateDir()))
            .platforms(valueProvider.getList(BUILDX_PLATFORMS, config.getPlatforms()))
            .attestations(extractAttestations(config.getAttestations(), valueProvider))
            .cacheFrom(valueProvider.getString(BUILDX_CACHE_FROM, config.getCacheFrom()))
            .cacheTo(valueProvider.getString(BUILDX_CACHE_TO, config.getCacheTo()))
            .build();
    }

    private AttestationConfiguration extractAttestations(AttestationConfiguration config, ValueProvider valueProvider) {
        if (config == null) {
            config = new AttestationConfiguration();
        }

        return new AttestationConfiguration.Builder()
            .provenance(valueProvider.getString(BUILDX_ATTESTATION_PROVENANCE, config.getProvenance()))
            .sbom(valueProvider.getBoolean(BUILDX_ATTESTATION_SBOM, config.getSbom()))
            .build();
    }

    // Extract only the values of the port mapping

    private List<String> extractPortValues(List<String> config, ValueProvider valueProvider) {
        List<String> ports = valueProvider.getList(PORTS, config);
        if (ports == null || ports.isEmpty()) {
            return null;
        }
        List<String> ret = new ArrayList<>();
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
        if (config == null) {
            config = new RestartPolicy();
        }

        return new RestartPolicy.Builder()
            .name(valueProvider.getString(RESTART_POLICY_NAME, config.getName()))
            .retry(valueProvider.getInt(RESTART_POLICY_RETRY, config.getRetry() == 0 ? null : config.getRetry()))
            .build();
    }

    private LogConfiguration extractLogConfig(LogConfiguration config, ValueProvider valueProvider) {
        if (config == null) {
            config = new LogConfiguration();
        }

        return new LogConfiguration.Builder()
            .color(valueProvider.getString(LOG_COLOR, config.getColor()))
            .date(valueProvider.getString(LOG_DATE, config.getDate()))
            .file(valueProvider.getString(LOG_FILE, config.getFileLocation()))
            .prefix(valueProvider.getString(LOG_PREFIX, config.getPrefix()))
            .logDriverName(valueProvider.getString(LOG_DRIVER_NAME, config.getDriver() == null ? null : config.getDriver().getName()))
            .logDriverOpts(valueProvider.getMap(LOG_DRIVER_OPTS, config.getDriver() == null ? null : config.getDriver().getOpts()))
            .enabled(valueProvider.getBoolean(LOG_ENABLED, config.isEnabled()))
            .build();
    }

    private WaitConfiguration extractWaitConfig(WaitConfiguration config, ValueProvider valueProvider) {
        if (config == null) {
            config = new WaitConfiguration();
        }

        WaitConfiguration.ExecConfiguration exec = config.getExec();
        if (exec == null) {
            exec = new WaitConfiguration.ExecConfiguration();
        }

        WaitConfiguration.HttpConfiguration http = config.getHttp();
        if (http == null) {
            http = new WaitConfiguration.HttpConfiguration();
        }

        WaitConfiguration.TcpConfiguration tcp = config.getTcp();
        if (tcp == null) {
            tcp = new WaitConfiguration.TcpConfiguration();
        }

        String url = valueProvider.getString(WAIT_HTTP_URL, config.getUrl());
        if (url == null) {
            // Fallback to deprecated old URL
            url = valueProvider.getString(WAIT_URL, config.getUrl());
        }
        return new WaitConfiguration.Builder()
            .time(valueProvider.getInt(WAIT_TIME, config.getTime()))
            .healthy(valueProvider.getBoolean(WAIT_HEALTHY, config.getHealthy()))
            .url(url)
            .preStop(valueProvider.getString(WAIT_EXEC_PRE_STOP, exec.getPreStop()))
            .postStart(valueProvider.getString(WAIT_EXEC_POST_START, exec.getPostStart()))
            .breakOnError(valueProvider.getBoolean(WAIT_EXEC_BREAK_ON_ERROR, exec.isBreakOnError()))
            .method(valueProvider.getString(WAIT_HTTP_METHOD, http.getMethod()))
            .status(valueProvider.getString(WAIT_HTTP_STATUS, http.getStatus()))
            .log(valueProvider.getString(WAIT_LOG, config.getLog()))
            .kill(valueProvider.getInteger(WAIT_KILL, config.getKill()))
            .exit(valueProvider.getInteger(WAIT_EXIT, config.getExit()))
            .shutdown(valueProvider.getInteger(WAIT_SHUTDOWN, config.getShutdown()))
            .tcpHost(valueProvider.getString(WAIT_TCP_HOST, tcp.getHost()))
            .tcpPorts(valueProvider.getIntList(WAIT_TCP_PORT, tcp.getPorts()))
            .tcpMode(valueProvider.getString(WAIT_TCP_MODE, tcp.getMode() == null ? null : tcp.getMode().name()))
            .build();
    }

    private WatchImageConfiguration extractWatchConfig(ImageConfiguration fromConfig, ValueProvider valueProvider) {
        WatchImageConfiguration config = fromConfig.getWatchConfiguration();
        if (config == null) {
            config = new WatchImageConfiguration();
        }

        return new WatchImageConfiguration.Builder()
            .interval(valueProvider.getInteger(WATCH_INTERVAL, config.getIntervalRaw()))
            .postGoal(valueProvider.getString(WATCH_POSTGOAL, config.getPostGoal()))
            .postExec(valueProvider.getString(WATCH_POSTEXEC, config.getPostExec()))
            .mode(valueProvider.getString(WATCH_POSTGOAL, config.getMode() == null ? null : config.getMode().name()))
            .build();
    }

    private CopyConfiguration extractCopyConfig(ImageConfiguration fromConfig, ValueProvider valueProvider) {
        CopyConfiguration config = fromConfig.getCopyConfiguration();
        if (config == null) {
            config = new CopyConfiguration();
        }

        return new CopyConfiguration.Builder()
            .entriesAsListOfProperties(valueProvider.getPropertiesList(COPY_ENTRIES, config.getEntriesAsListOfProperties()))
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
        if (config == null) {
            config = new RunVolumeConfiguration();
        }

        return new RunVolumeConfiguration.Builder()
            .bind(valueProvider.getList(BIND, config.getBind()))
            .from(valueProvider.getList(VOLUMES_FROM, config.getFrom()))
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
        if (externalConfig == null || externalConfig.isEmpty()) {
            return false;
        }

        if (!TYPE_NAME.equals(externalConfig.get("type"))) {
            // This images loads config from something totally different
            return true;
        }

        // This image has a specified prefix. If multiple images have explicitly set docker. as prefix we
        // assume user know what they are doing and allow it.
        return externalConfig.get("prefix") != null;
    }
}
