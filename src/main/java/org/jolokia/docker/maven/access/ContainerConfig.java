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
public class ContainerConfig {

    private final String imageName;

    private final JSONObject object = new JSONObject();

    public ContainerConfig(String imageName) {
        this.imageName = imageName;
        object.put("Image", imageName);
    }

    public ContainerConfig bind(List<String> bind) {
        if (bind != null && !bind.isEmpty()) {
            JSONObject volumes = new JSONObject();
            for (String volume : bind) {
                if (volume.contains(":")) {
                    volume = volume.split(":")[0];
                }
                volumes.put(volume, new JSONObject());
            }
            object.put("Volumes", volumes);
        }
        return this;
    }

    public ContainerConfig command(String command) {
        if (command != null) {
            JSONArray a = new JSONArray();
            for (String s : EnvUtil.splitWOnSpaceWithEscape(command)) {
                a.put(s);
            }
            object.put("Cmd", a);
        }
        return this;
    }

    public ContainerConfig domainname(String domainname) {
        add("Domainname", domainname);
        return this;
    }

    public ContainerConfig entrypoint(String entrypoint) {
        if (entrypoint != null) {
            add("Entrypoint", entrypoint);
        }
        return this;
    }

    public ContainerConfig environment(Map<String, String> env) throws IllegalArgumentException {
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
            object.put("Env", a);
        }
        return this;
    }

    public ContainerConfig exposedPorts(Set<Integer> ports) {
        if (ports != null && ports.size() > 0) {
            JSONObject exposedPorts = new JSONObject();
            for (Integer port : ports) {
                exposedPorts.put(port.toString() + "/tcp", new JSONObject());
            }
            object.put("ExposedPorts", exposedPorts);
        }
        return this;
    }

    public String getImageName() {
        return imageName;
    }

    public ContainerConfig hostname(String hostname) {
        add("Hostname", hostname);
        return this;
    }

    public ContainerConfig memory(long memory) {
        if (memory != 0) {
            object.put("Memory", memory);
        }
        return this;
    }

    public ContainerConfig memorySwap(long memorySwap) {
        if (memorySwap != 0) {
            object.put("MemorySwap", memorySwap);
        }
        return this;
    }

    public String toJson() {
        return object.toString();
    }

    public ContainerConfig user(String user) {
        add("User", user);
        return this;
    }

    public ContainerConfig workingDir(String workingDir) {
        add("WorkingDir", workingDir);
        return this;
    }

    private void add(String name, String value) {
        if (value != null) {
            object.put(name, value);
        }
    }
}
