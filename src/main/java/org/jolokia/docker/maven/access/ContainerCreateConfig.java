package org.jolokia.docker.maven.access;

import java.io.*;
import java.util.*;

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

    public ContainerCreateConfig environment(String envPropsFile, Map<String, String> env) throws IllegalArgumentException {

        Properties envProps = new Properties();
        if (env != null && env.size() > 0) {
            envProps.putAll(env);
        }
        if (envPropsFile != null) {
            // Props from external file take precedence
            addPropertiesFromFile(envPropsFile, envProps);
        }

        if (envProps.size() > 0) {
            addEnvironment(envProps);
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

    private void addEnvironment(Properties envProps) {
        JSONArray containerEnv = new JSONArray();
        Enumeration keys = envProps.keys();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            String value = envProps.getProperty(key);
            if (value == null || value.length() == 0) {
                throw new IllegalArgumentException(String.format("Env variable '%s' must not be null or empty",key));
            }
            containerEnv.put(key + "=" + value);
        }
        createConfig.put("Env", containerEnv);
    }

    private void addPropertiesFromFile(String envPropsFile, Properties envProps) {
        // External properties override internally specified properties
        try {
            FileReader reader = new FileReader(envPropsFile);
            envProps.load(reader);
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException(String.format("Cannot find environment property file '%s'", envPropsFile));
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Error while loading environment properties: %s", e.getMessage()), e);
        }
    }
}
