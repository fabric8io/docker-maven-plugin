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

import io.fabric8.maven.docker.config.*;
import io.fabric8.maven.docker.config.handler.ExternalConfigHandler;
import io.fabric8.maven.docker.util.EnvUtil;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

import java.util.*;

import static io.fabric8.maven.docker.config.handler.property.ConfigKey.*;
import static io.fabric8.maven.docker.util.EnvUtil.extractFromPropertiesAsList;
import static io.fabric8.maven.docker.util.EnvUtil.extractFromPropertiesAsMap;

/**
 * @author roland
 * @since 18/11/14
 */
// Moved temporarily to resources/META-INF/plexus/components.xml because of https://github.com/codehaus-plexus/plexus-containers/issues/4
// @Component(role = ExternalConfigHandler.class)
public class PropertyConfigHandler implements ExternalConfigHandler {

    @Override
    public String getType() {
        return "properties";
    }

    @Override
    public List<ImageConfiguration> resolve(final ImageConfiguration config, final MavenProject project, final MavenSession session)
            throws IllegalArgumentException {
        final String prefix = getPrefix(config);
        final Properties properties = project.getProperties();


        final RunImageConfiguration run = extractRunConfiguration(prefix, properties);
        final BuildImageConfiguration build = extractBuildConfiguration(prefix, properties);
        final WatchImageConfiguration watch = extractWatchConfig(prefix, properties);

        final String name = extractName(prefix, properties);
        final String alias = withPrefix(prefix, ALIAS, properties);

        return Collections.singletonList(
                new ImageConfiguration.Builder()
                        .name(name)
                        .alias(alias != null ? alias : config.getAlias())
                        .runConfig(run)
                        .buildConfig(build)
                        .watchConfig(watch)
                        .build());
    }

    private BuildImageConfiguration extractBuildConfiguration(final String prefix, final Properties properties) {
        return new BuildImageConfiguration.Builder()
                .cmd(withPrefix(prefix, CMD, properties))
                .cleanup(withPrefix(prefix, CLEANUP, properties))
                .nocache(withPrefix(prefix, NOCACHE, properties))
                .optimise(withPrefix(prefix, OPTIMISE, properties))
                .entryPoint(withPrefix(prefix, ENTRYPOINT, properties))
                .assembly(extractAssembly(prefix, properties))
                .env(mapWithPrefix(prefix, ENV, properties))
                .args(mapWithPrefix(prefix, ARGS, properties))
                .labels(mapWithPrefix(prefix, LABELS, properties))
                .ports(extractPortValues(prefix, properties))
                .runCmds(extractRunCommands(prefix, properties))
                .from(withPrefix(prefix, FROM, properties))
                .fromExt(mapWithPrefix(prefix, FROM_EXT, properties))
                .registry(withPrefix(prefix, REGISTRY, properties))
                .volumes(listWithPrefix(prefix, VOLUMES, properties))
                .tags(listWithPrefix(prefix, TAGS, properties))
                .maintainer(withPrefix(prefix, MAINTAINER, properties))
                .workdir(withPrefix(prefix, WORKDIR, properties))
                .skip(withPrefix(prefix, SKIP_BUILD, properties))
                .dockerFile(withPrefix(prefix, DOCKER_FILE, properties))
                .dockerFileDir(withPrefix(prefix, DOCKER_FILE_DIR, properties))
                .user(withPrefix(prefix, USER, properties))
                .healthCheck(extractHealthCheck(prefix, properties))
                .build();
    }

    private RunImageConfiguration extractRunConfiguration(final String prefix, final Properties properties) {

        return new RunImageConfiguration.Builder()
                .capAdd(listWithPrefix(prefix, CAP_ADD, properties))
                .capDrop(listWithPrefix(prefix, CAP_DROP, properties))
                .securityOpts(listWithPrefix(prefix, SECURITY_OPTS, properties))
                .cmd(withPrefix(prefix, CMD, properties))
                .dns(listWithPrefix(prefix, DNS, properties))
                .dependsOn(listWithPrefix(prefix, DEPENDS_ON, properties))
                .net(withPrefix(prefix, NET, properties))
                .network(extractNetworkConfig(prefix, properties))
                .dnsSearch(listWithPrefix(prefix, DNS_SEARCH, properties))
                .domainname(withPrefix(prefix, DOMAINNAME, properties))
                .entrypoint(withPrefix(prefix, ENTRYPOINT, properties))
                .env(mapWithPrefix(prefix, ENV, properties))
                .labels(mapWithPrefix(prefix, LABELS, properties))
                .envPropertyFile(withPrefix(prefix, ENV_PROPERTY_FILE, properties))
                .extraHosts(listWithPrefix(prefix, EXTRA_HOSTS, properties))
                .hostname(withPrefix(prefix, HOSTNAME, properties))
                .links(listWithPrefix(prefix, LINKS, properties))
                .memory(longWithPrefix(prefix, MEMORY, properties))
                .memorySwap(longWithPrefix(prefix, MEMORY_SWAP, properties))
                .namingStrategy(withPrefix(prefix, NAMING_STRATEGY, properties))
                .exposedPropertyKey(withPrefix(prefix, EXPOSED_PROPERTY_KEY, properties))
                .portPropertyFile(withPrefix(prefix, PORT_PROPERTY_FILE, properties))
                .ports(listWithPrefix(prefix, PORTS, properties))
                .shmSize(longWithPrefix(prefix, SHMSIZE, properties))
                .privileged(booleanWithPrefix(prefix, PRIVILEGED, properties))
                .restartPolicy(extractRestartPolicy(prefix, properties))
                .user(withPrefix(prefix, USER, properties))
                .workingDir(withPrefix(prefix, WORKING_DIR, properties))
                .log(extractLogConfig(prefix, properties))
                .wait(extractWaitConfig(prefix, properties))
                .volumes(extractVolumeConfig(prefix, properties))
                .skip(withPrefix(prefix, SKIP_RUN, properties))
                .ulimits(extractUlimits(prefix, properties))
                .tmpfs(listWithPrefix(prefix, TMPFS, properties))
                .keepEnvs(withPrefix(prefix, KEEP_ENVS, properties))
                .build();
    }

    private NetworkConfig extractNetworkConfig(final String prefix, final Properties properties) {
        return new NetworkConfig.Builder()
                .mode(withPrefix(prefix, NETWORK_MODE, properties))
                .name(withPrefix(prefix, NETWORK_NAME, properties))
                .aliases(listWithPrefix(prefix, NETWORK_ALIAS, properties))
                .build();
    }

    private AssemblyConfiguration extractAssembly(final String prefix, final Properties properties) {
        return new AssemblyConfiguration.Builder()
                .targetDir(withPrefix(prefix, ASSEMBLY_BASEDIR, properties))
                .descriptor(withPrefix(prefix, ASSEMBLY_DESCRIPTOR, properties))
                .descriptorRef(withPrefix(prefix, ASSEMBLY_DESCRIPTOR_REF, properties))
                .dockerFileDir(withPrefix(prefix, ASSEMBLY_DOCKER_FILE_DIR, properties))
                .exportBasedir(booleanWithPrefix(prefix, ASSEMBLY_EXPORT_BASEDIR, properties))
                .ignorePermissions(booleanWithPrefix(prefix, ASSEMBLY_IGNORE_PERMISSIONS, properties))
                .permissions(withPrefix(prefix, ASSEMBLY_PERMISSIONS, properties))
                .user(withPrefix(prefix, ASSEMBLY_USER, properties))
                .mode(withPrefix(prefix, ASSEMBLY_MODE, properties))
                .tarLongFileMode(withPrefix(prefix, ASSEMBLY_TARLONGFILEMODE, properties))
                .build();
    }

    private HealthCheckConfiguration extractHealthCheck(final String prefix, final Properties properties) {
        final Map<String, String> healthCheckProperties = mapWithPrefix(prefix, HEALTHCHECK, properties);
        if (healthCheckProperties != null && healthCheckProperties.size() > 0) {
            return new HealthCheckConfiguration.Builder()
                    .interval(withPrefix(prefix, HEALTHCHECK_INTERVAL, properties))
                    .timeout(withPrefix(prefix, HEALTHCHECK_TIMEOUT, properties))
                    .retries(intWithPrefix(prefix, HEALTHCHECK_RETRIES, properties))
                    .mode(withPrefix(prefix, HEALTHCHECK_MODE, properties))
                    .cmd(withPrefix(prefix, HEALTHCHECK_CMD, properties))
                    .build();
        }

        return null;
    }

    private String extractName(final String prefix, final Properties properties) throws IllegalArgumentException {
        final String name = withPrefix(prefix, NAME, properties);
        if (name == null) {
            throw new IllegalArgumentException(String.format("Mandatory property [%s] is not defined", NAME));
        }
        return name;
    }

    // Extract only the values of the port mapping
    private List<String> extractPortValues(final String prefix, final Properties properties) {
        final List<String> ret = new ArrayList<>();
        final List<String> ports = listWithPrefix(prefix, PORTS, properties);
        if (ports == null) {
            return null;
        }
        final List<String[]> parsedPorts = EnvUtil.splitOnLastColon(ports);
        for (final String[] port : parsedPorts) {
            ret.add(port[1]);
        }
        return ret;
    }


    private List<String> extractRunCommands(final String prefix, final Properties properties) {
        final List<String> ret = new ArrayList<>();
        final List<String> cmds = listWithPrefix(prefix, RUN, properties);
        if (cmds == null) {
            return null;
        }
        return ret;
    }

    private RestartPolicy extractRestartPolicy(final String prefix, final Properties properties) {
        return new RestartPolicy.Builder()
                .name(withPrefix(prefix, RESTART_POLICY_NAME, properties))
                .retry(asInt(withPrefix(prefix, RESTART_POLICY_RETRY, properties)))
                .build();
    }

    private LogConfiguration extractLogConfig(final String prefix, final Properties properties) {
        final LogConfiguration.Builder builder = new LogConfiguration.Builder()
                .color(withPrefix(prefix, LOG_COLOR, properties))
                .date(withPrefix(prefix, LOG_DATE, properties))
                .prefix(withPrefix(prefix, LOG_PREFIX, properties))
                .logDriverName(withPrefix(prefix, LOG_DRIVER_NAME, properties))
                .logDriverOpts(mapWithPrefix(prefix, LOG_DRIVER_OPTS, properties));
        final Boolean enabled = booleanWithPrefix(prefix, LOG_ENABLED, properties);
        if (enabled != null) {
            builder.enabled(enabled);
        }
        return builder.build();
    }

    private WaitConfiguration extractWaitConfig(final String prefix, final Properties properties) {
        String url = withPrefix(prefix, WAIT_HTTP_URL, properties);
        if (url == null) {
            // Fallback to deprecated old URL
            url = withPrefix(prefix, WAIT_URL, properties);
        }
        return new WaitConfiguration.Builder()
                .time(asInt(withPrefix(prefix, WAIT_TIME, properties)))
                .url(url)
                .preStop(withPrefix(prefix, PRE_STOP, properties))
                .postStart(withPrefix(prefix, POST_START, properties))
                .method(withPrefix(prefix, WAIT_HTTP_METHOD, properties))
                .status(withPrefix(prefix, WAIT_HTTP_STATUS, properties))
                .log(withPrefix(prefix, WAIT_LOG, properties))
                .kill(asInt(withPrefix(prefix, WAIT_KILL, properties)))
                .shutdown(asInt(withPrefix(prefix, WAIT_SHUTDOWN, properties)))
                .tcpHost(withPrefix(prefix, WAIT_TCP_HOST, properties))
                .tcpPorts(asIntList(listWithPrefix(prefix, WAIT_TCP_PORT, properties)))
                .tcpMode(withPrefix(prefix, WAIT_TCP_MODE, properties))
                .build();
    }

    private WatchImageConfiguration extractWatchConfig(final String prefix, final Properties properties) {
        return new WatchImageConfiguration.Builder()
                .interval(asInt(withPrefix(prefix, WATCH_INTERVAL, properties)))
                .postGoal(withPrefix(prefix, WATCH_POSTGOAL, properties))
                .mode(withPrefix(prefix, WATCH_POSTGOAL, properties))
                .build();
    }

    private List<UlimitConfig> extractUlimits(final String prefix, final Properties properties) {
        final List<String> ulimits = listWithPrefix(prefix, ConfigKey.ULIMITS, properties);
        if (ulimits == null) {
            return null;
        }
        final List<UlimitConfig> ret = new ArrayList<>();
        for (final String ulimit : ulimits) {
            ret.add(new UlimitConfig(ulimit));
        }
        return ret;
    }

    private VolumeConfiguration extractVolumeConfig(final String prefix, final Properties properties) {
        return new VolumeConfiguration.Builder()
                .bind(listWithPrefix(prefix, BIND, properties))
                .from(listWithPrefix(prefix, VOLUMES_FROM, properties))
                .build();
    }

    private int asInt(final String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private List<Integer> asIntList(final List<String> strings) {
        if (strings == null) {
            return null;
        }

        final List<Integer> ints = new ArrayList<>();
        for (final String s : strings) {
            ints.add(asInt(s));
        }

        return ints;

    }

    private List<String> listWithPrefix(final String prefix, final ConfigKey key, final Properties properties) {
        return extractFromPropertiesAsList(key.asPropertyKey(prefix), properties);
    }

    private Map<String, String> mapWithPrefix(final String prefix, final ConfigKey key, final Properties properties) {
        return extractFromPropertiesAsMap(key.asPropertyKey(prefix), properties);
    }

    private String withPrefix(final String prefix, final ConfigKey key, final Properties properties) {
        return properties.getProperty(key.asPropertyKey(prefix));
    }

    private Integer intWithPrefix(final String prefix, final ConfigKey key, final Properties properties) {
        final String prop = withPrefix(prefix, key, properties);
        return prop == null ? null : Integer.valueOf(prop);
    }

    private Long longWithPrefix(final String prefix, final ConfigKey key, final Properties properties) {
        final String prop = withPrefix(prefix, key, properties);
        return prop == null ? null : Long.valueOf(prop);
    }

    private Boolean booleanWithPrefix(final String prefix, final ConfigKey key, final Properties properties) {
        final String prop = withPrefix(prefix, key, properties);
        return prop == null ? null : Boolean.valueOf(prop);
    }

    private String getPrefix(final ImageConfiguration config) {
        String prefix = config.getExternalConfig().get("prefix");
        if (prefix == null) {
            prefix = "docker";
        }
        return prefix;
    }
}
