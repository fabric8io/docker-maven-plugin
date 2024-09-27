package io.fabric8.maven.docker.config.handler.compose;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.HealthCheckConfiguration;
import io.fabric8.maven.docker.config.HealthCheckMode;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.LogConfiguration;
import io.fabric8.maven.docker.config.NetworkConfig;
import io.fabric8.maven.docker.config.RestartPolicy;
import io.fabric8.maven.docker.config.RunVolumeConfiguration;
import io.fabric8.maven.docker.config.UlimitConfig;
import io.fabric8.maven.docker.config.WaitConfiguration;
import io.fabric8.maven.docker.util.VolumeBindingUtil;


class DockerComposeServiceWrapper {

    private final Map<String, Object> configuration;
    private final String name;
    private final File composeFile;
    private final ImageConfiguration enclosingImageConfig;
    private final File baseDir;

    DockerComposeServiceWrapper(String serviceName, File composeFile, Map<String, Object> serviceDefinition,
                                ImageConfiguration enclosingImageConfig, File baseDir) {
        this.name = serviceName;
        this.composeFile = composeFile;
        this.configuration = serviceDefinition;
        this.enclosingImageConfig = enclosingImageConfig;

        if (!baseDir.isAbsolute()) {
            throw new IllegalArgumentException(
                    "Expected the base directory '" + baseDir + "' to be an absolute path.");
        }
        this.baseDir = baseDir;
    }

    String getAlias() {
        // 'container_name' takes precidence
        String alias = asString("container_name");
        return (alias != null) ? alias : name;
    }

    String getImage() {
        return asString("image");
    }

    // ==================================================================================
    // Build config:

    boolean requiresBuild() {
        return asObject("build") != null;
    }

    String getBuildDir() {
        Object build = asObject("build");
        if (build == null) {
            return null;
        }
        if (build instanceof String) {
            return (String) build;
        }
        if (! (build instanceof Map)) {
            throwIllegalArgumentException("build:' must be either a String or a Map");
        }
        Map<String, String> buildConfig = (Map<String, String>) build;
        if (!buildConfig.containsKey("context")) {
            throwIllegalArgumentException("'build:' a context directory for a build must be specified");
        }
        return buildConfig.get("context");
    }

    String getDockerfile() {
        Object build = asObject("build");
        if (build instanceof Map) {
            return (String) ((Map) build).get("dockerfile");
        } else {
            return null;
        }
    }

    Map<String, String> getBuildArgs() {
        Object build = asObject("build");
        if (build instanceof Map) {
            return (Map<String, String>) ((Map) build).get("args");
        } else {
            return null;
        }
    }

    // ===================================================================================
    // Run config:

    List<String> getCapAdd() {
        return asList("cap_add");
    }

    List<String> getCapDrop() {
        return asList("cap_drop");
    }

    Map<String, String> getSysctls() {
        return asMap("sysctls");
    }

    Arguments getCommand() {
        Object command = asObject("command");
        return command != null ? asArguments(command, "command") : null;
    }

    List<String> getDependsOn() {
        try {
            return asList("depends_on");
        // With the new long style of depends_on since compose 2.1+, this may be a map.
        // Maps' keys still are the container names though.
        } catch (ClassCastException e) {
            return new ArrayList<>(asMap("depends_on").keySet());
        }
    }

    boolean usesLongSyntaxDependsOn() {
        return asObject("depends_on") instanceof Map;
    }

    public String getPlatform() {
        return asString("platform");
    }

    public HealthCheckConfiguration getHealthCheckConfiguration() {
        if (!configuration.containsKey("healthcheck")) {
            return null;
        }
        Map<String, Object> healthCheckAsMap = (Map<String, Object>) configuration.get("healthcheck");
        HealthCheckConfiguration.Builder builder = new HealthCheckConfiguration.Builder();
        Object disable = healthCheckAsMap.get("disable");
        if (isaBoolean(disable)) {
            builder.mode(HealthCheckMode.none);
            return builder.build();
        }

        Object test = healthCheckAsMap.get("test");
        if (test == null) {
            return null;
        }
        if (test instanceof List) {
            List<String> cmd = (List<String>) test;
            if (cmd.size() > 0 && cmd.get(0).equalsIgnoreCase("NONE")) {
                builder.mode(HealthCheckMode.none);
                return builder.build();
            } else {
                builder.cmd(new Arguments((List<String>) test));
                builder.mode(HealthCheckMode.cmd);
            }
        } else {
            builder.cmd(new Arguments(Arrays.asList("CMD-SHELL", test.toString())));
            builder.mode(HealthCheckMode.cmd);
        }
        enableWaitCondition(WaitCondition.HEALTHY);

        Object interval = healthCheckAsMap.get("interval");
        if (interval != null) {
            builder.interval(interval.toString());
        }
        Object timeout = healthCheckAsMap.get("timeout");
        if (timeout != null) {
            builder.timeout(timeout.toString());
        }
        Object retries = healthCheckAsMap.get("retries");
        if (retries != null && retries instanceof Number) {
            builder.retries(((Number) retries).intValue());
        }
        Object startPeriod = healthCheckAsMap.get("start_period");
        if (startPeriod != null) {
            builder.startPeriod(startPeriod.toString());
        }

        return builder.build();
    }

    private static boolean isaBoolean(Object disable) {
        return disable != null && disable instanceof Boolean && (Boolean) disable;
    }

    /**
     * <a href="https://docs.docker.com/compose/compose-file/compose-file-v2/#depends_on">Docker Compose Spec v2.1+ defined conditions</a>
     */
    enum WaitCondition {
        HEALTHY("service_healthy"),
        COMPLETED("service_completed_successfully"),
        STARTED("service_started");

        private final String condition;
        WaitCondition(String condition) {
            this.condition = condition;
        }

        static WaitCondition fromString(String string) {
            return Arrays.stream(WaitCondition.values()).filter(wc -> wc.condition.equals(string)).findFirst().orElseThrow(
                () -> new IllegalArgumentException("invalid condition \"" + string + "\"")
            );
        }
    }

    /**
     * Extract a required condition of another (dependent) service from this service.
     * In a compose file following v2.1+ format this looks like this:
     * <pre>{@code
     * services:
     *   web:
     *     build: .
     *     depends_on:
     *       db:
     *         condition: service_healthy
     *       redis:
     *         condition: service_started
     * }</pre>
     * If this is the "web" service and the parameter to this method is "db", it will extract
     * the validated condition via {@link WaitCondition}.
     *
     * @param dependentServiceName The dependent service's name (not alias!)
     * @return The waiting condition if valid
     * @throws IllegalArgumentException When condition cannot be extracted or is invalid
     */
    WaitCondition getWaitCondition(String dependentServiceName) {
        Objects.requireNonNull(dependentServiceName, "Dependent service's name may not be null");

        Object dependsOnObj = asObject("depends_on");
        if (dependsOnObj instanceof Map) {
            Map<String, Object> dependsOn = (Map<String, Object>) dependsOnObj;
            Object dependenSvcObj = dependsOn.get(dependentServiceName);

            if (dependenSvcObj instanceof Map) {
                Map<String, String> dependency = (Map<String,String>) dependenSvcObj;

                if (dependency.containsKey("condition")) {
                    String condition = dependency.get("condition");
                    try {
                        return WaitCondition.fromString(condition);
                    } catch (IllegalArgumentException e) {
                        throwIllegalArgumentException("depends_on service \"" + dependentServiceName +
                            "\" has invalid syntax (" + e.getMessage() + ")");
                    }
                }
                throwIllegalArgumentException("depends_on service \"" + dependentServiceName + "\" has invalid syntax (missing condition)");
            }
            throwIllegalArgumentException("depends_on service \"" + dependentServiceName + "\" has invalid syntax (no map)");
        }
        throwIllegalArgumentException("depends_on does not use long syntax, cannot retrieve condition");
        return null;
    }

    private boolean healthyWaitRequested;
    private boolean successExitWaitRequested;

    /**
     * Switch on waiting conditions for this service.
     * It will not yet check for conflicting conditions, this is done in {@link #getWaitConfiguration()}
     * when building the image configurations.
     * @param condition The condition to enable for this service
     */
    void enableWaitCondition(WaitCondition condition) {
        Objects.requireNonNull(condition, "Condition may not be null");

        // We do not check for conflicting conditions here - this is done when the wrapper is asked for its WaitConfig
        // Note: yes, we check here again, as we rely on Strings, not an enum
        switch (condition) {
            case HEALTHY:
                this.healthyWaitRequested = true;
                break;
            case COMPLETED:
                this.successExitWaitRequested = true;
                break;
            case STARTED:
                // No need to do anything here.
                // This is equivalent to the short syntax and simple startup ordering doesn't need a wait configuration.
            default:
                // Do nothing when unknown condition
        }
    }

    /**
     * Build the actual wait configuration for this service.
     * <p>Please note: while Docker Compose allows you to create a dependency graph which will allow to wait
     * for a dependent service to exit from one service and at the same time wait for it to be healthy from another,
     * this is not possible with this Maven Plugin.</p>
     * <p>The reasoning behind this limitation is rooted in the data model. Docker Compose attaches the
     * requested conditions at the depending service level, while this plugin only supports wait conditions
     * on the dependent service. Once these are fulfilled, all depending services will be started.</p>
     * @return The wait configuration
     */
    WaitConfiguration getWaitConfiguration() {
        if (successExitWaitRequested && healthyWaitRequested) {
            throwIllegalArgumentException("Conflicting requested conditions \"service_healthy\" and \"service_completed_successfully\" for this service");
        } else if (healthyWaitRequested) {
            return new WaitConfiguration.Builder().healthy(healthyWaitRequested).build();
        } else if (successExitWaitRequested) {
            return new WaitConfiguration.Builder().exit(0).build();
        }
        return null;
    }

    List<String> getDns() {
        return asList("dns");
    }

    List<String> getDnsSearch() {
        return asList("dns_search");
    }

    List<String> getTmpfs() {
        return asList("tmpfs");
    }

    Arguments getEntrypoint() {
        Object entrypoint = asObject("entrypoint");
        return entrypoint != null ? asArguments(entrypoint, "entrypoint") : null;
    }

    Map<String, String> getEnvironment() {
        // TODO: load the env files from compose as given with env_file and add them all to the map
        return asMap("environment");
    }

    // external_links are added with "links"

    List<String> getExtraHosts() {
        return asList("extra_hosts");
    }

    // image is added as top-level in image configuration

    Map<String, String> getLabels() {
        return asMap("labels");
    }

    public List<String> getLinks() {
        List<String> ret = new ArrayList<>();
        ret.addAll(this.<String>asList("links"));
        ret.addAll(this.<String>asList("external_links"));
        return ret;
    }

    LogConfiguration getLogConfiguration() {
        Object logConfig = asObject("logging");
        if (logConfig == null) {
            return null;
        }
        if (!(logConfig instanceof Map)) {
            throwIllegalArgumentException("'logging' has to be a map and not " + logConfig.getClass());
        }
        Map<String, Object> config = (Map<String, Object>) logConfig;
        return new LogConfiguration.Builder()
            .logDriverName((String) config.get("driver"))
            .logDriverOpts((Map<String, String>) config.get("options"))
            .build();
    }

    NetworkConfig getNetworkConfig() {
        String net = asString("network_mode");
        if (net != null) {
            return new NetworkConfig(net);
        }
        Object networks = asObject("networks");
        if (networks == null) {
            return null;
        }
        if (networks instanceof List) {
            List<String> toJoin = (List<String>) networks;
            if (toJoin.size() > 1) {
                throwIllegalArgumentException("'networks:' Only one custom network to join is supported currently");
            }
            return new NetworkConfig(NetworkConfig.Mode.custom, toJoin.get(0));
        } else if (networks instanceof Map) {
            Map<String,Object> toJoin = (Map<String, Object>) networks;
            if (toJoin.size() > 1) {
                throwIllegalArgumentException("'networks:' Only one custom network to join is supported currently");
            }
            String custom = toJoin.keySet().iterator().next();
            NetworkConfig ret = new NetworkConfig(NetworkConfig.Mode.custom, custom);
            Object aliases = toJoin.get(custom);
            if (aliases != null) {
                if (aliases instanceof List) {
                    for (String alias : (List<String>) aliases) {
                        ret.addAlias(alias);
                    }
                } else if (aliases instanceof Map) {
                	Map<String, List<String>> map = (Map<String, List<String>>) aliases;
                    if (map.containsKey("aliases")) {
                        for (String alias : map.get("aliases")) {
                            ret.addAlias(alias);
                        }
                    } else {
                        throwIllegalArgumentException(
                                "'networks:' Aliases must be given as a map of strings. 'aliases' key not founded");
                    }
                } else {
                    throwIllegalArgumentException("'networks:' No aliases entry found in network config map");
                }
            }
            return ret;
        } else {
            throwIllegalArgumentException("'networks:' must beu either a list or a map");
            return null;
        }
    }

    List<String> getPortMapping() {
        List<String> fromYml = asList("ports");
        int size = fromYml.size();

        List<String> ports = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String port = fromYml.get(i);
            if (port.contains(":")) {
                ports.add(port);
            }
            else {
                /*
                 * docker-compose allows just the port number which triggers a random port and the plugin does not, so construct a property
                 * name to mimic the required behavior. names will always based on position, and not the number of times we create the
                 * string.
                 */
                ports.add(String.format("%s_port_%s:%s", getAlias(), i + 1, port));
            }
        }
        return ports;
    }

    List<UlimitConfig> getUlimits() {
        Object ulimits = asObject("ulimits");
        if (ulimits == null) {
            return null;
        }
        if (!(ulimits instanceof Map)) {
            throwIllegalArgumentException("'ulimits:' must be a map");
        }
        Map<String, Object> ulimitMap = (Map<String, Object>) ulimits;
        List<UlimitConfig> ret = new ArrayList<>();
        for (String ulimit : ulimitMap.keySet()) {
            Object val = ulimitMap.get(ulimit);
            if (val instanceof Map) {
                Map<String, Integer> valMap = (Map<String, Integer>) val;
                Integer soft = valMap.get("soft");
                Integer hard = valMap.get("hard");
                ret.add(new UlimitConfig(ulimit, hard, soft));
            } else if (val instanceof Integer) {
                ret.add(new UlimitConfig(ulimit, (Integer) val, null));
            } else {
                throwIllegalArgumentException("'ulimits:' invalid limit value " + val + " (class : " + val.getClass() + ")");
            }
        }
        return ret;
    }

    RunVolumeConfiguration getVolumeConfig() {
        RunVolumeConfiguration.Builder builder = new RunVolumeConfiguration.Builder();
        List<String> volumes = asList("volumes");
        boolean added = false;
        if (!volumes.isEmpty()) {
            builder.bind(volumes);
            added = true;
        }
        List<String> volumesFrom = asList("volumes_from");
        if (!volumesFrom.isEmpty()) {
            builder.from(volumesFrom);
            added = true;
        }

        if (added) {
            RunVolumeConfiguration runVolumeConfiguration = builder.build();
            VolumeBindingUtil.resolveRelativeVolumeBindings(baseDir, runVolumeConfiguration);
            return runVolumeConfiguration;
        }

        return null;
    }

    String getDomainname() {
        return asString("domainname");
    }

    String getHostname() {
        return asString("hostname");
    }

    Long getMemory() {
        return asLong("mem_limit");
    }

    Long getMemorySwap() {
        return asLong("memswap_limit");
    }

    Boolean getPrivileged() {
        return asBoolean("privileged");
    }

    RestartPolicy getRestartPolicy() {
        String restart = asString("restart");
        if (restart == null) {
            return null;
        }

        RestartPolicy.Builder builder = new RestartPolicy.Builder();
        if (restart.contains(":")) {
            String[] parts = restart.split(":", 2);
            builder.name(parts[0]).retry(Integer.valueOf(parts[1]));
        }
        else {
            builder.name(restart);
        }

        return builder.build();
    }

    Long getShmSize() {
        return asLong("shm_size");
    }

    String getUser() {
        return asString("user");
    }

    String getWorkingDir() {
        return asString("working_dir");
    }

    // ================================================================
    // Not used yet:

    public String getCGroupParent() {
        return asString("cgroup_parent");
    }

    public String getCpuSet() {
        return asString("cpuset");
    }

    public String getIsolation() {return  asString("isolation"); }

    public Long getCpuShares() {
        return asLong("cpu_shares");
    }

    public Double getCpusCount(){
        return asDouble("cpus");
    }

    public List<String> getDevices() {
        return asList("devices");
    }

    // =======================================================================================================
    // Helper methods

    private Object asObject(String key) {
        return configuration.get(key);
    }

    private String asString(String key) {
        return String.class.cast(configuration.get(key));
    }

    private Long asLong(String key) {
        Long value = null;
        if (configuration.containsKey(key)) {
            value = Long.valueOf(configuration.get(key).toString());
        }
        return value;
    }

    private Boolean asBoolean(String key) {
        Boolean value = null;
        if (configuration.containsKey(key)) {
            value = Boolean.valueOf(configuration.get(key).toString());
        }
        return value;
    }

    private <T> List<T> asList(String key) {
        if (configuration.containsKey(key)) {
            Object value = configuration.get(key);
            if (value instanceof String) {
                value = Arrays.asList(value);
            }

            return List.class.cast(value);
        }

        return Collections.emptyList();
    }

    private Map<String, String> asMap(String key) {
        if (configuration.containsKey(key)) {
            Object value = configuration.get(key);
            if (value instanceof List) {
                value = convertToMap(List.class.cast(value));
            }
            return Map.class.cast(value);
        }

        return Collections.emptyMap();
    }

    private Double asDouble(String key){
        Double value = null;
        if (configuration.containsKey(key)) {
            value = Double.valueOf(configuration.get(key).toString());
        }
        return value;
    }

    private Arguments asArguments(Object command, String label) {
        if (command instanceof String) {
            return new Arguments((String) command);
        } else if (command instanceof List) {
            return new Arguments((List<String>) command);
        } else {
            throwIllegalArgumentException(String.format("'%s' must be either String or List but not %s", label, command.getClass()));
            return null;
        }
    }

    private Map<String, String> convertToMap(List<String> list) {
        Map<String, String> map = new HashMap<>(list.size());
        for (String entry : list) {
            String[] parts = entry.split("=", 2);
            map.put(parts[0], parts[1]);
        }
        return map;
    }

    void throwIllegalArgumentException(String msg) {
        throw new IllegalArgumentException(String.format("%s: %s - ", composeFile, name) + msg);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DockerComposeServiceWrapper)) return false;
        DockerComposeServiceWrapper that = (DockerComposeServiceWrapper) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}

