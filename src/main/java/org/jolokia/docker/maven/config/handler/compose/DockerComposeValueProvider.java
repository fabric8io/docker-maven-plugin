package org.jolokia.docker.maven.config.handler.compose;

import java.util.*;

import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.config.RestartPolicy;
import org.jolokia.docker.maven.config.RestartPolicy.Builder;
import org.jolokia.docker.maven.config.RunImageConfiguration.NamingStrategy;
import org.jolokia.docker.maven.config.WaitConfiguration;
import org.jolokia.docker.maven.config.WatchImageConfiguration;

class DockerComposeValueProvider {

    private final Map<String, Object> configuration;
    private final ImageConfiguration extended;

    private final String service;

    public DockerComposeValueProvider(String service, Map<String, Object> configuration, ImageConfiguration extended) {
        this.service = service;
        this.configuration = configuration;
        this.extended = extended;
    }

    public String getAlias() {
        // 'container_name' takes precidence
        String alias = getString("container_name");
        return (alias != null) ? alias : service;
    }

    public List<String> getBindVolumes() {
        return getList("volumes");
    }

    public String getBuildDir() {
        return getString("build");
    }

    public List<String> getCapAdd() {
        return getList("cap_add");
    }

    public List<String> getCapDrop() {
        return getList("cap_drop");
    }

    public String getCGroupParent() {
        return getString("cgroup_parent");
    }

    public String getCleanup() {
        return toString(extended.getBuildConfiguration().cleanup());
    }

    public String getCommand() {
        return getString("command");
    }

    public String getCompression() {
        return extended.getBuildConfiguration().getCompression().name();
    }

    public String getCpuSet() {
        return getString("cpu_set");
    }

    public String getCpuShares() {
        return getString("cpu_shares");
    }

    public List<String> getDevices() {
        return getList("devices");
    }

    public List<String> getDns() {
        return getList("dns");
    }

    public List<String> getDnsSearch() {
        return getList("dns_search");
    }

    public String getDomainName() {
        return getString("domainname");
    }

    public String getEntrypoint() {
        return getString("entrypoint");
    }

    public Map<String, String> getEnvironment() {
        // TODO: load the env files from compose and add them all to the map
        return getMap("environment");
    }

    public List<String> getExtraHosts() {
        return getList("extra_hosts");
    }

    public String getHostname() {
        return getString("hostname");
    }

    public String getImage() {
        // use the image name defined in the service when building
        return (getBuildDir() != null) ? extended.getName() : getString("image");
    }

    public Map<String, String> getLabels() {
        return getMap("labels");
    }

    public List<String> getLinks() {
        return getList("links");
    }

    public Long getMemory() {
        return getLong("mem_limit");
    }

    public Long getMemorySwap() {
        return getLong("memswap_limit");
    }

    public String getNamingStrategy() {
        return NamingStrategy.alias.toString();
    }

    public String getPortPropertyFile() {
        return extended.getRunConfiguration().getPortPropertyFile();
    }

    public Boolean getPrivileged() {
        return getBoolean("privileged");
    }

    public RestartPolicy getRestartPolicy() {
        String restart = getString("restart");
        if (restart == null) {
            return null;
        }

        Builder builder = new RestartPolicy.Builder();
        if (restart.contains(":")) {
            String[] parts = restart.split("\\:", 2);
            builder.name(parts[0]).retry(Integer.valueOf(parts[1]));
        }
        else {
            builder.name(restart);
        }

        return builder.build();
    }

    public List<String> getRunPorts() {
        List<String> fromYml = getList("ports");
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

    public String getSkipBuild() {
        return toString(extended.getBuildConfiguration().skip());
    }

    public String getSkipRun() {
        return toString(extended.getRunConfiguration().skip());
    }

    private String toString(boolean bool) {
        return String.valueOf(bool);
    }
    
    public String getString(String key) {
        return String.class.cast(configuration.get(key));
    }

    public List<String> getTags() {
        return Collections.emptyList();
    }

    public String getUser() {
        return getString("user");
    }

    public List<String> getVolumes() {
        return getList("volumes");
    }

    public List<String> getVolumesFrom() {
        return getList("volumes_from");
    }

    public WaitConfiguration getWaitConfiguration() {
        return extended.getRunConfiguration().getWaitConfiguration();
    }

    public WatchImageConfiguration getWatchImageConfiguration() {
        return extended.getWatchConfiguration();
    }

    public String getWorkingDir() {
        return getString("working_dir");
    }

    public boolean requiresBuild() {
        return (getBuildDir() != null) ? true : false;
    }

    private Map<String, String> convertToMap(List<String> list) {
        Map<String, String> map = new HashMap<>(list.size());
        for (String entry : list) {
            String[] parts = entry.split("=", 2);
            map.put(parts[0], parts[1]);
        }
        return map;
    }

    private Boolean getBoolean(String key) {
        Boolean value = null;
        if (configuration.containsKey(key)) {
            value = Boolean.valueOf(configuration.get(key).toString());
        }
        return value;
    }

    private <T> List<T> getList(String key) {
        if (configuration.containsKey(key)) {
            Object value = configuration.get(key);
            if (value instanceof String) {
                value = Arrays.asList(value);
            }

            return List.class.cast(value);
        }

        return Collections.emptyList();
    }

    private Long getLong(String key) {
        Long value = null;
        if (configuration.containsKey(key)) {
            value = Long.valueOf(configuration.get(key).toString());
        }
        return value;
    }

    private Map<String, String> getMap(String key) {
        if (configuration.containsKey(key)) {
            Object value = configuration.get(key);
            if (value instanceof List) {
                value = convertToMap(List.class.cast(value));
            }

            return Map.class.cast(value);
        }

        return Collections.emptyMap();
    }
}
