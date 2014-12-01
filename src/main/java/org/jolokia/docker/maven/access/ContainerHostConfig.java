package org.jolokia.docker.maven.access;

import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;


/**
 * Represents a configuration used to start a container
 */
public class ContainerHostConfig {

    private final JSONObject object = new JSONObject();

    public ContainerHostConfig capAdd(List<String> capAdd) {
        return addAsArray("CapAdd",capAdd);
    }

    public ContainerHostConfig capDrop(List<String> capDrop) {
        return addAsArray("CapDrop", capDrop);
    }

    public ContainerHostConfig dns(List<String> dns) {
        return addAsArray("Dns",dns);
    }

    public ContainerHostConfig dnsSearch(List<String> dnsSearch) {
        return addAsArray("DnsSearch",dnsSearch);
    }

    public ContainerHostConfig links(List<String> links) {
        return addAsArray("Links",links);
    }

    public ContainerHostConfig volumesFrom(List<String> volumesFrom) {
        return addAsArray("VolumesFrom",volumesFrom);
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

            object.put("PortBindings", portBindings);
        }
        return this;
    }

    public ContainerHostConfig bind(List<String> bind) {
        if (bind != null && !bind.isEmpty()) {
            JSONArray host = new JSONArray();
            for (String volume : bind) {
                if (volume.contains(":")) {
                    host.put(volume);
                }
            }
            object.put("Binds", host);
        }
        return this;
    }

    public ContainerHostConfig privileged(boolean privileged) {
        object.put("Privileged", privileged);
        return this;
    }

    public ContainerHostConfig restartPolicy(String name, int retry)
    {
        if (name != null) {
            JSONObject policy = new JSONObject();
            policy.put("Name", name);
            policy.put("MaximumRetryCount", retry);

            object.put("RestartPolicy", policy);
        }
        return this;
    }

    public String toJson() {
        return object.toString();
    }

    private ContainerHostConfig addAsArray(String propKey, List<String> props) {
        if (props != null) {
            object.put(propKey, new JSONArray(props));
        };
        return this;
    }
}
