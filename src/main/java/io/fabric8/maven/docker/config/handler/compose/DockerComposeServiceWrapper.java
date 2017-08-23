package io.fabric8.maven.docker.config.handler.compose;

import java.io.File;
import java.util.*;

import io.fabric8.maven.docker.config.*;


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

    Arguments getCommand() {
        Object command = asObject("command");
        return command != null ? asArguments(command, "command") : null;
    }

    List<String> getDependsOn() {
        return asList("depends_on");
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
            return new NetworkConfig(NetworkConfig.Mode.custom, name);
        } else if (networks instanceof Map) {
            Map<String,Object> toJoin = (Map<String, Object>) networks;
            if (toJoin.size() > 1) {
                throwIllegalArgumentException("'networks:' Only one custom network to join is supported currently");
            }
            String custom = toJoin.keySet().iterator().next();
            NetworkConfig ret = new NetworkConfig(NetworkConfig.Mode.custom, custom);
            Object aliases = toJoin.get(custom);
            if (aliases != null) {
                if (!(aliases instanceof List)) {
                    throwIllegalArgumentException("'networks:' Aliases must be given as a list of string");
                }
                for (String alias : (List<String>) aliases) {
                    ret.addAlias(alias);
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
//        String base = "/Users/esm/workspaces/rmap/rmap/indexing-solr/src/test/docker/";
        if (volumes.size() > 0) {
            for (int i = 0; i < volumes.size(); i++) {
                String resolvedVolume = resolveRelativeVolumeBinding(baseDir, volumes.get(i));
                System.out.println("*** Resolved '" + volumes.get(i) + "' to '" + resolvedVolume + "'");
                volumes.set(i, resolveRelativeVolumeBinding(baseDir, volumes.get(i)));
            }
            builder.bind(volumes);
            added = true;
        }
        List<String> volumesFrom = asList("volumes_from");
        if (volumesFrom.size() > 0) {
            builder.from(volumesFrom);
            added = true;
        }
        return added ? builder.build() : null;
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
            String[] parts = restart.split("\\:", 2);
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
        return asString("cpu_set");
    }

    public String getCpuShares() {
        return asString("cpu_shares");
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

    private void throwIllegalArgumentException(String msg) {
        throw new IllegalArgumentException(String.format("%s: %s - ", composeFile, name) + msg);
    }

    /**
     * Examines the supplied {@code volume} string, and resolves relative paths.  The returned string is guaranteed to
     * be absolute, but <em>not</em> guaranteed to exist.
     * <p>
     * Volumes may be defined inside of {@code service} blocks <a href="https://docs.docker.com/compose/compose-file/compose-file-v2/#volumes-volume_driver">
     * as documented here</a>:
     * </p>
     * <pre>
     * volumes:
     * # Just specify a path and let the Engine create a volume
     * - /var/lib/mysql
     *
     * # Specify an absolute path mapping
     * - /opt/data:/var/lib/mysql
     *
     * # Path on the host, relative to the Compose file
     * - ./cache:/tmp/cache
     *
     * # User-relative path
     * - ~/configs:/etc/configs/:ro
     *
     * # Named volume
     * - datavolume:/var/lib/mysql"
     * </pre>
     * <p>
     * This method only operates on volume strings that are relative: beginning with {@code ./} or {@code ~/}.  Relative
     * paths beginning with {@code ./} are absolutized relative to the supplied base directory, which <em>must</em> be
     * absolute.  Paths beginning with {@code ~/} are interpreted relative to {@code new File(System.getProperty(
     * "user.home"))}, and {@code baseDir} is ignored.
     * </p>
     * <p>
     * Volume strings that do not begin with a {@code ./} or {@code ~/} are returned as-is.
     * </p>
     * <h3>Examples:</h3>
     * <p>
     * Given {@code baseDir} equal to "/path/to/basedir" and a {@code volume} string equal to
     * "./reldir:/some/other/dir", this method returns {@code /path/to/basedir/reldir:/some/other/dir}
     * </p>
     * <p>
     * Given {@code baseDir} equal to "/path/to/basedir" and a {@code volume} string equal to
     * "~/reldir:/some/other/dir", this method returns {@code /path/to/user/home/reldir:/some/other/dir}
     * </p>
     *
     * @param baseDir the base directory used to resolve paths beginning with {@code ./}; <em>must</em> be absolute
     * @param volume the volume string from the docker-compose file
     * @return the volume string, with any relative paths resolved as absolute paths
     */
    static String resolveRelativeVolumeBinding(File baseDir, String volume) {

        if (!baseDir.isAbsolute()) {
            throw new IllegalArgumentException("Base directory '" + baseDir + "' must be absolute.");
        }

        // a 'services:' -> service -> 'volumes:' may be formatted as:
        // (https://docs.docker.com/compose/compose-file/compose-file-v2/#volumes-volume_driver)
        //
        // volumes:
        //  # Just specify a path and let the Engine create a volume
        //  - /var/lib/mysql
        //
        //  # Specify an absolute path mapping
        //  - /opt/data:/var/lib/mysql
        //
        //  # Path on the host, relative to the Compose file
        //  - ./cache:/tmp/cache
        //
        //  # User-relative path
        //  - ~/configs:/etc/configs/:ro
        //
        //  # Named volume
        // - datavolume:/var/lib/mysql

        String[] pathParts = volume.split(":");
        String localPath = pathParts[0];
        String serverPath = (pathParts.length > 1) ? pathParts[1] : "";

        // only perform resolution if the localPath begins with '~/' or './'

        if (localPath.startsWith("./")) {
            localPath = new File(baseDir, localPath.substring(2)).getAbsolutePath();
        }

        if (localPath.startsWith("~/")) {
            localPath = new File(System.getProperty("user.home"), localPath.substring(2)).getAbsolutePath();
        }

        if (serverPath.length() > 0) {
            return String.format("%s:%s", localPath, serverPath);
        }

        return localPath;
    }
}
