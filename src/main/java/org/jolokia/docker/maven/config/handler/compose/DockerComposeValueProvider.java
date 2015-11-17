package org.jolokia.docker.maven.config.handler.compose;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jolokia.docker.maven.config.RunImageConfiguration.NamingStrategy;
import org.jolokia.docker.maven.config.WaitConfiguration;
import org.jolokia.docker.maven.config.WatchImageConfiguration;
import org.jolokia.docker.maven.config.external.DockerComposeConfiguration.Service;

class DockerComposeValueProvider {

    private final Map<String, Object> configuration;
    private final Service extended;

    private final String service;

    public DockerComposeValueProvider(String service, Map<String, Object> configuration, Service extended) {
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
        return Collections.emptyList();
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

    public String getCommand() {
        return getString("command");
    }

    public List<String> getDevices() {
        return Collections.emptyList();
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
        // environment
        return Collections.emptyMap();
    }

    public String getEnvPropertyFile() {
        // TODO: can be one or multiple...
        return null;
    }

    public List<String> getExtraHosts() {
        return getList("extra_hosts");
    }

    public String getHostname() {
        return getString("hostname");
    }

    public String getImage() {
        // use the image name defined in the service when building
        return (getBuildDir() != null) ? extended.getImage() : getString("image");
    }

    public Map<String, String> getLabels() {
        // TODO: list or dictionary
        return Collections.emptyMap();
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
        return null;
    }

    public Boolean getPrivileged() {
        return getBoolean("privileged");
    }
    
    public String getRestartPolicyName() {
        return null;
    }

    public int getRestartPolicyRetry() {
        return 0;
    }

    public List<String> getRunCommands() {
        return Collections.emptyList();
    }

    public List<String> getRunPorts() {
        return Collections.emptyList();
    }

    public Service getServiceConfiguration() {
        return extended;
    }

    public String getSkipRun() {
        return null;
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
        return Collections.emptyList();
    }

    public List<String> getVolumesFrom() {
        return Collections.emptyList();
    }

    public WaitConfiguration getWaitConfiguration() {
        return (extended == null) ? null : extended.getWaitConfiguration();
    }

    public WatchImageConfiguration getWatchImageConfiguration() {
        return (extended == null) ? null : extended.getWatchImageConfiguration();
    }

    public String getWorkingDir() {
        return getString("working_dir");
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
}
