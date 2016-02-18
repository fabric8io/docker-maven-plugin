package org.jolokia.docker.maven.access;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jolokia.docker.maven.config.LogConfig;
import org.json.JSONArray;
import org.json.JSONObject;

public class ContainerHostConfig {

    final JSONObject startConfig = new JSONObject();

    public ContainerHostConfig() {}

    public ContainerHostConfig binds(List<String> bind) {
        if (bind != null && !bind.isEmpty()) {
            JSONArray binds = new JSONArray();

            for (String volume : bind) {
                if (volume.contains(":")) {
                    // Hack-fix for mounting on Windows where the ${projectDir} variable and other
                    // contain backslashes and what not. Related to #188
                    volume = volume.replace("\\", "/").replaceAll("^(?i:C:)", "/c");
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

    public ContainerHostConfig dns(List<String> dns) {
        return addAsArray("Dns", dns);
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

    public ContainerHostConfig links(List<String> links) {
        return addAsArray("Links", links);
    }

    public ContainerHostConfig portBindings(PortMapping portMapping) {
        Map<String, Integer> portMap = portMapping.getContainerPortToHostPortMap();
        if (!portMap.isEmpty()) {
            JSONObject portBindings = new JSONObject();
            Map<String, String> bindToMap = portMapping.getBindToHostMap();

            for (Map.Entry<String, Integer> entry : portMap.entrySet()) {
                String containerPortSpec = entry.getKey();
                Integer hostPort = entry.getValue();

                JSONObject o = new JSONObject();
                o.put("HostPort", hostPort != null ? hostPort.toString() : "");

                if (bindToMap.containsKey(containerPortSpec)) {
                    o.put("HostIp", bindToMap.get(containerPortSpec));
                }

                JSONArray array = new JSONArray();
                array.put(o);

                portBindings.put(containerPortSpec, array);
            }

            startConfig.put("PortBindings", portBindings);
        }
        return this;
    }

    public ContainerHostConfig privileged(Boolean privileged) {
        return add("Privileged", privileged);
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

    public ContainerHostConfig logConfig(LogConfig logConfig) {
        if (logConfig != null) {
            JSONObject logConfigJson = new JSONObject();
            logConfigJson.put("Type", logConfig.getLogDriver());
            JSONObject config = new JSONObject();
            for(Map.Entry<String, String> logOpt : logConfig.getLogOpts().entrySet()) {
                config.put(logOpt.getKey(), logOpt.getValue());
            }
            logConfigJson.put("Config", config);
            startConfig.put("LogConfig", logConfigJson);
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