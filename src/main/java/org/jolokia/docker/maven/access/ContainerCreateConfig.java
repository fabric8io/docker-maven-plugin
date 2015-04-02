package org.jolokia.docker.maven.access;

import java.util.*;

import org.apache.commons.lang3.text.StrSubstitutor;
import org.jolokia.docker.maven.util.EnvUtil;
import org.json.JSONArray;
import org.json.JSONObject;

public class ContainerCreateConfig {
    final JSONObject createConfig = new JSONObject();
    final String imageName;

    public ContainerCreateConfig(String imageName) {
        this.imageName = imageName;
        createConfig.put("Image", imageName);
    }

    public ContainerCreateConfig binds(List<String> volumes) {
        if (volumes != null && !volumes.isEmpty()) {
            JSONObject extractedVolumes = new JSONObject();

            for (String volume : volumes) {
                extractedVolumes.put(extractContainerPath(volume),
                                     new JSONObject());
            }
            createConfig.put("Volumes", extractedVolumes);
        }
        return this;
    }

    public ContainerCreateConfig command(String command) {
        if (command != null) {
            createConfig.put("Cmd", splitOnWhiteSpace(command));
        }
        return this;
    }

    public ContainerCreateConfig domainname(String domainname) {
        return add("Domainname", domainname);
    }

    public ContainerCreateConfig entrypoint(String entrypoint) {
        if (entrypoint != null) {
            createConfig.put("Entrypoint", splitOnWhiteSpace(entrypoint));
        }
        return this;
    }

    private JSONArray splitOnWhiteSpace(String entrypoint) {
        JSONArray a = new JSONArray();
        for (String s : EnvUtil.splitWOnSpaceWithEscape(entrypoint)) {
            a.put(s);
        }
        return a;
    }

    public ContainerCreateConfig environment(Map<String, String> env, Properties properties) throws IllegalArgumentException {
        if (env != null && env.size() > 0) {
            JSONArray a = new JSONArray();
            for (Map.Entry<String, String> entry : env.entrySet()) {
                String value = entry.getValue();
                if (value == null || value.length() == 0) {
                    throw new IllegalArgumentException(String.format("Env variable '%s' must not be null or empty",
                                                                     entry.getKey()));
                }

                StrSubstitutor substitutor = new StrSubstitutor();
                String jsonString = entry.getKey() + "=" + substitutor.replace(value, properties);
                a.put(jsonString);
            }
            createConfig.put("Env", a);
        }
        return this;
    }

    public ContainerCreateConfig exposedPorts(Set<String> portSpecs) {
        if (portSpecs != null && portSpecs.size() > 0) {
            JSONObject exposedPorts = new JSONObject();
            for (String portSpec : portSpecs) {
                exposedPorts.put(portSpec, new JSONObject());
            }
            createConfig.put("ExposedPorts", exposedPorts);
        }
        return this;
    }

    public String getImageName() {
        return imageName;
    }

    public ContainerCreateConfig hostname(String hostname) {
        return add("Hostname", hostname);
    }

    public ContainerCreateConfig memory(Long memory) {
        return add("Memory", memory);
    }

    public ContainerCreateConfig memorySwap(Long memorySwap) {
        return add("MemorySwap", memorySwap);
    }

    public ContainerCreateConfig user(String user) {
        return add("User", user);
    }

    public ContainerCreateConfig workingDir(String workingDir) {
        return add("WorkingDir", workingDir);
    }

    public ContainerCreateConfig hostConfig(ContainerHostConfig startConfig) {
        return add("HostConfig", startConfig.toJsonObject());
    }

    /**
     * Get JSON which is used for <em>creating</em> a container
     *
     * @return string representation for JSON representing creating a container
     */
    public String toJson() {
        return createConfig.toString();
    }

    // =======================================================================

    private ContainerCreateConfig add(String name, Object value) {
        if (value != null) {
            createConfig.put(name, value);
        }
        return this;
    }

    private String extractContainerPath(String volume) {
        if (volume.contains(":")) {
            String[] parts = volume.split(":");
            if (parts.length > 1) {
                return parts[1];
            }
        }
        return volume;
    }
}