package io.fabric8.maven.docker.access;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.fabric8.maven.docker.config.LogConfiguration;
import io.fabric8.maven.docker.config.UlimitConfig;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.JsonFactory;

public class ContainerHostConfig {

    final JsonObject startConfig = new JsonObject();

    public ContainerHostConfig() {}

    public ContainerHostConfig binds(List<String> bind) {
        if (bind != null && !bind.isEmpty()) {
            JsonArray binds = new JsonArray();

            for (String volume : bind) {
                volume = EnvUtil.fixupPath(volume);

                if (volume.contains(":")) {
                    binds.add(volume);
                }
            }
            startConfig.add("Binds", binds);
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

    public ContainerHostConfig cpuShares(Long cpuShares) {
        return add("CpuShares", cpuShares);
    }

    public ContainerHostConfig cpus(Long cpus) {
        return add ("NanoCpus", cpus);
    }

    public ContainerHostConfig cpuSet(String cpuSet) {
        return add("CpusetCpus", cpuSet);
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
            JsonArray ulimits = new JsonArray();
            for (UlimitConfig ulimit : ulimitsConfig) {
                JsonObject ulimitConfigJson = new JsonObject();
                ulimitConfigJson.addProperty("Name", ulimit.getName());
                addIfNotNull(ulimitConfigJson, "Hard", ulimit.getHard());
                addIfNotNull(ulimitConfigJson, "Soft", ulimit.getSoft());
                ulimits.add(ulimitConfigJson);
            }

            startConfig.add("Ulimits", ulimits);
        }
        return this;
    }

    private void addIfNotNull(JsonObject json, String key, Integer value) {
        if (value != null) {
            json.addProperty(key, value);
        }
    }

    public ContainerHostConfig links(List<String> links) {
        return addAsArray("Links", links);
    }

    public ContainerHostConfig portBindings(PortMapping portMapping) {
        JsonObject portBindings = portMapping.toDockerPortBindingsJson();
        if (portBindings != null) {
            startConfig.add("PortBindings", portBindings);
        }
        return this;
    }

    public ContainerHostConfig privileged(Boolean privileged) {
        return add("Privileged", privileged);
    }

    public ContainerHostConfig tmpfs(List<String> mounts) {
        if (mounts != null && mounts.size() > 0) {
            JsonObject tmpfs = new JsonObject();
            for (String mount : mounts) {
                int idx = mount.indexOf(':');
                if (idx > -1) {
                    tmpfs.addProperty(mount.substring(0,idx), mount.substring(idx+1));
                } else {
                    tmpfs.addProperty(mount, "");
                }
            }
            startConfig.add("Tmpfs", tmpfs);
        }
        return this;
    }

    public ContainerHostConfig shmSize(Long shmSize) {
        return add("ShmSize", shmSize);
    }

    public ContainerHostConfig restartPolicy(String name, int retry) {
        if (name != null) {
            JsonObject policy = new JsonObject();
            policy.addProperty("Name", name);
            policy.addProperty("MaximumRetryCount", retry);

            startConfig.add("RestartPolicy", policy);
        }
        return this;
    }

    public ContainerHostConfig logConfig(LogConfiguration logConfig) {
        if (logConfig != null) {
            LogConfiguration.LogDriver logDriver = logConfig.getDriver();
            if (logDriver != null) {
                JsonObject logConfigJson = new JsonObject();
                logConfigJson.addProperty("Type", logDriver.getName());

                Map<String,String> opts = logDriver.getOpts();
                if (opts != null && opts.size() > 0) {
                    JsonObject config = new JsonObject();
                    for (Map.Entry<String, String> logOpt : opts.entrySet()) {
                        config.addProperty(logOpt.getKey(), logOpt.getValue());
                    }
                    logConfigJson.add("Config", config);
                }

                startConfig.add("LogConfig", logConfigJson);
            }
        }
        return this;
    }
    
    public ContainerHostConfig readonlyRootfs(Boolean readOnly) {
        return add("ReadonlyRootfs", readOnly);
    }

    public ContainerHostConfig autoRemove(Boolean autoRemove) {
        return add("AutoRemove", autoRemove);
    }

    /**
     * Get JSON which is used for <em>starting</em> a container
     *
     * @return string representation for JSON representing the configuration for starting a container
     */
    public String toJson() {
        return startConfig.toString();
    }

    public JsonObject toJsonObject() {
        return startConfig;
    }

    ContainerHostConfig addAsArray(String propKey, List<String> props) {
        if (props != null) {
            startConfig.add(propKey, JsonFactory.newJsonArray(props));
        }
        return this;
    }

    private ContainerHostConfig add(String name, String value) {
        if (value != null) {
            startConfig.addProperty(name, value);
        }
        return this;
    }

    private ContainerHostConfig add(String name, Boolean value) {
        if (value != null) {
            startConfig.addProperty(name, value);
        }
        return this;
    }

    private ContainerHostConfig add(String name, Long value) {
        if (value != null) {
            startConfig.addProperty(name, value);
        }
        return this;
    }
}
