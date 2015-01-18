package org.jolokia.docker.maven.access;

import java.util.List;
import java.util.Map;

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

    public ContainerHostConfig extraHosts(List<String> extraHosts) {
        return addAsArray("ExtraHosts", extraHosts);
    }

    public ContainerHostConfig volumesFrom(List<String> volumesFrom) {
        return addAsArray("VolumesFrom", volumesFrom);
    }

    public ContainerHostConfig links(List<String> links) {
        return addAsArray("Links", links);
    }

    public ContainerHostConfig portBindings(PortMapping portMapping) {
        Map<Integer, Integer> portMap = portMapping.getPortsMap();
        if (!portMap.isEmpty()) {
            JSONObject portBindings = new JSONObject();
            Map<Integer, String> bindToMap = portMapping.getBindToMap();

            for (Map.Entry<Integer, Integer> entry : portMap.entrySet()) {
                Integer port = entry.getKey();
                Integer hostPort = entry.getValue();

                JSONObject o = new JSONObject();
                o.put("HostPort", hostPort != null ? hostPort.toString() : "");

                if (bindToMap.containsKey(port)) {
                    o.put("HostIp", bindToMap.get(port));
                }

                JSONArray array = new JSONArray();
                array.put(o);

                portBindings.put(port + "/tcp", array);
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