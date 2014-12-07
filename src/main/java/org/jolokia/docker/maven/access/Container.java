package org.jolokia.docker.maven.access;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jolokia.docker.maven.util.EnvUtil;
import org.json.JSONArray;
import org.json.JSONObject;


/**
 * Represents a configuration used to create a container
 */
public class Container {

    private final JSONObject container = new JSONObject();
    private String containerId;

    private final JSONObject hostConfig = new JSONObject();
    private final String imageName;

    public Container(String imageName) {
        this.imageName = imageName;
        container.put("Image", imageName);
    }

    public Container binds(List<String> bind) {
        if (bind != null && !bind.isEmpty()) {
            JSONObject volumes = new JSONObject();
            JSONArray binds = new JSONArray();

            for (String volume : bind) {
                if (volume.contains(":")) {
                    binds.put(volume);
                    volume = volume.split(":")[0];
                }
                volumes.put(volume, new JSONObject());
            }

            container.put("Volumes", volumes);
            hostConfig.put("Binds", binds);
        }
        return this;
    }

    public Container capAdd(List<String> capAdd) {
        return addAsArray(hostConfig, "CapAdd", capAdd);
    }

    public Container capDrop(List<String> capDrop) {
        return addAsArray(hostConfig, "CapDrop", capDrop);
    }

    public Container command(String command) {
        if (command != null) {
            JSONArray a = new JSONArray();
            for (String s : EnvUtil.splitWOnSpaceWithEscape(command)) {
                a.put(s);
            }
            container.put("Cmd", a);
        }
        return this;
    }

    public Container dns(List<String> dns) {
        return addAsArray(hostConfig, "Dns", dns);
    }

    public Container dnsSearch(List<String> dnsSearch) {
        return addAsArray(hostConfig, "DnsSearch", dnsSearch);
    }

    public Container domainname(String domainname) {
        return add(container, "Domainname", domainname);
    }

    public Container entrypoint(String entrypoint) {
        return add(container, "Entrypoint", entrypoint);
    }

    public Container environment(Map<String, String> env) throws IllegalArgumentException {
        if (env != null && env.size() > 0) {
            JSONArray a = new JSONArray();
            for (Map.Entry<String, String> entry : env.entrySet()) {
                String value = entry.getValue();
                if (value == null || value.length() == 0) {
                    throw new IllegalArgumentException(String.format("Env variable '%s' must not be null or empty when running %s",
                            entry.getKey(), imageName));
                }
                a.put(entry.getKey() + "=" + entry.getValue());
            }
            container.put("Env", a);
        }
        return this;
    }

    public Container exposedPorts(Set<Integer> ports) {
        if (ports != null && ports.size() > 0) {
            JSONObject exposedPorts = new JSONObject();
            for (Integer port : ports) {
                exposedPorts.put(port.toString() + "/tcp", new JSONObject());
            }
            container.put("ExposedPorts", exposedPorts);
        }
        return this;
    }

    public Container extraHosts(List<String> extraHosts) {
        return addAsArray(hostConfig, "ExtraHosts", extraHosts);
    }
    
    public String getContainerId() {
        return containerId.substring(0, 12);
    }

    public String getImageName() {
        return imageName;
    }

    public Container hostname(String hostname) {
        return add(container, "Hostname", hostname);
    }

    public Container links(List<String> links) {
        return addAsArray(hostConfig, "Links", links);
    }

    public Container memory(Long memory) {
        return add(container,"Memory",memory);
    }

    public Container memorySwap(Long memorySwap) {
        return add(container,"MemorySwap",memorySwap);
    }

    public Container portBindings(PortMapping portMapping) {
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

            hostConfig.put("PortBindings", portBindings);
        }
        return this;
    }

    public Container privileged(Boolean privileged) {
        return add(hostConfig,"Privileged",privileged);
    }

    public Container restartPolicy(String name, int retry) {
        if (name != null) {
            JSONObject policy = new JSONObject();
            policy.put("Name", name);
            policy.put("MaximumRetryCount", retry);

            hostConfig.put("RestartPolicy", policy);
        }
        return this;
    }

    public String toCreateJson() {
        return container.toString();
    }

    public String toStartJson() {
        return hostConfig.toString();
    }

    public Container user(String user) {
        return add(container, "User", user);
    }

    public Container volumesFrom(List<String> volumesFrom) {
        return addAsArray(hostConfig, "VolumesFrom", volumesFrom);
    }

    public Container workingDir(String workingDir) {
        return add(container, "WorkingDir", workingDir);
    }

    void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    private Container add(JSONObject object, String name, Object value) {
        if (value != null) {
            object.put(name, value);
        }
        return this;
    }

    private Container addAsArray(JSONObject object, String propKey, List<String> props) {
        if (props != null) {
            object.put(propKey, new JSONArray(props));
        }
        ;
        return this;
    }
}
