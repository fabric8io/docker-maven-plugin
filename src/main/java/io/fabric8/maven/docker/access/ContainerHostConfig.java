package io.fabric8.maven.docker.access;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.fabric8.maven.docker.config.LogConfiguration;
import io.fabric8.maven.docker.config.UlimitConfig;
import io.fabric8.maven.docker.util.EnvUtil;
import org.json.JSONArray;
import org.json.JSONObject;

public class ContainerHostConfig {

    final JSONObject startConfig = new JSONObject();

    public ContainerHostConfig() {}

    public ContainerHostConfig binds(List<String> bind) {
        if (bind != null && !bind.isEmpty()) {
            JSONArray binds = new JSONArray();

            for (String volume : bind) {
                volume = EnvUtil.fixupPath(volume);

                if (volume.contains(":")) {
                    binds.put(volume);
                }
            }
            startConfig.put("Binds", binds);
        }
        return this;
    }

    public ContainerHostConfig capAdd(List<String> capAdd) {
        return addAsArray("CapAdd", capAdd);
    }

    public ContainerHostConfig capDrop(List<String> capDrop) {
        return addAsArray("CapDrop", capDrop);
    }

    public ContainerHostConfig securityOpts(List<String> securityOpt) {
        return addAsArray("SecurityOpt", securityOpt);
    }

    public ContainerHostConfig memory(Long memory) {
        return add("Memory", memory);
    }

    public ContainerHostConfig memorySwap(Long memorySwap) {
        return add("MemorySwap", memorySwap);
    }

    public ContainerHostConfig dns(List<String> dns) {
        return addAsArray("Dns", dns);
    }

    public ContainerHostConfig networkMode(String net) {
        return add("NetworkMode",net);
    }

    public ContainerHostConfig dnsSearch(List<String> dnsSearch) {
        return addAsArray("DnsSearch", dnsSearch);
    }

    public ContainerHostConfig extraHosts(List<String> extraHosts) throws IllegalArgumentException {
        if (extraHosts != null) {
            List<String> mapped = new ArrayList<>();
            for (int i = 0; i < extraHosts.size(); i++) {
                String[] parts = extraHosts.get(i).split(":");
                if (parts.length == 1) {
                    throw new IllegalArgumentException("extraHosts must be in the form <host:host|ip>");
                }

                try {
                    mapped.add(i, parts[0] + ":" + InetAddress.getByName(parts[1]).getHostAddress());
                } catch (UnknownHostException e) {
                    throw new IllegalArgumentException("unable to resolve ip address for " + parts[1], e);
                }
            }
            return addAsArray("ExtraHosts", mapped);
        }
        return this;
    }

    public ContainerHostConfig volumesFrom(List<String> volumesFrom) {
        return addAsArray("VolumesFrom", volumesFrom);
    }

    public ContainerHostConfig ulimits(List<UlimitConfig> ulimitsConfig) {
    	if (ulimitsConfig != null && ulimitsConfig.size() > 0) {
            JSONArray ulimits = new JSONArray();
            for (UlimitConfig ulimit : ulimitsConfig) {
                JSONObject ulimitConfigJson = new JSONObject();
                ulimitConfigJson.put("Name", ulimit.getName());
                addIfNotNull(ulimitConfigJson, "Hard", ulimit.getHard());
                addIfNotNull(ulimitConfigJson, "Soft", ulimit.getSoft());
                ulimits.put(ulimitConfigJson);
            }

            startConfig.put("Ulimits", ulimits);
        }
        return this;
    }

    private void addIfNotNull(JSONObject json, String key, Integer value) {
        if (value != null) {
            json.put(key, value);
        }
    }

    public ContainerHostConfig links(List<String> links) {
        return addAsArray("Links", links);
    }

    public ContainerHostConfig portBindings(PortMapping portMapping) {
        JSONObject portBindings = portMapping.toDockerPortBindingsJson();
        if (portBindings != null) {
            startConfig.put("PortBindings", portBindings);
        }
        return this;
    }

    public ContainerHostConfig privileged(Boolean privileged) {
        return add("Privileged", privileged);
    }

    public ContainerHostConfig shmSize(Long shmSize) {
        return add("ShmSize", shmSize);
    }


    public ContainerHostConfig restartPolicy(String name, int retry) {
        if (name != null) {
            JSONObject policy = new JSONObject();
            policy.put("Name", name);
            policy.put("MaximumRetryCount", retry);

            startConfig.put("RestartPolicy", policy);
        }
        return this;
    }

    public ContainerHostConfig logConfig(LogConfiguration logConfig) {
        if (logConfig != null) {
            LogConfiguration.LogDriver logDriver = logConfig.getDriver();
            if (logDriver != null) {
                JSONObject logConfigJson = new JSONObject();
                logConfigJson.put("Type", logDriver.getName());

                Map<String,String> opts = logDriver.getOpts();
                if (opts != null && opts.size() > 0) {
                    JSONObject config = new JSONObject();
                    for (Map.Entry<String, String> logOpt : opts.entrySet()) {
                        config.put(logOpt.getKey(), logOpt.getValue());
                    }
                    logConfigJson.put("Config", config);
                }

                startConfig.put("LogConfig", logConfigJson);
            }
        }
        return this;
    }

    /**
     * Get JSON which is used for <em>starting</em> a container
     *
     * @return string representation for JSON representing the configuration for starting a container
     */
    public String toJson() {
        return startConfig.toString();
    }

    public Object toJsonObject() {
        return startConfig;
    }

    ContainerHostConfig addAsArray(String propKey, List<String> props) {
        if (props != null) {
            startConfig.put(propKey, new JSONArray(props));
        }
        return this;
    }

    private ContainerHostConfig add(String name, Object value) {
        if (value != null) {
            startConfig.put(name, value);
        }
        return this;
    }

}