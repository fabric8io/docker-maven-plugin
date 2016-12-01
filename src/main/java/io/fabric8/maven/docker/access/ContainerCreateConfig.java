package io.fabric8.maven.docker.access;

import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.util.EnvUtil;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class ContainerCreateConfig {

    private final JSONObject createConfig = new JSONObject();
    private final String imageName;

    public ContainerCreateConfig(final String imageName) {
        this.imageName = imageName;
        createConfig.put("Image", imageName);
    }

    public ContainerCreateConfig binds(final List<String> volumes) {
        if (volumes != null && !volumes.isEmpty()) {
            final JSONObject extractedVolumes = new JSONObject();

            for (final String volume : volumes) {
                extractedVolumes.put(extractContainerPath(volume),
                        new JSONObject());
            }
            createConfig.put("Volumes", extractedVolumes);
        }
        return this;
    }

    public ContainerCreateConfig command(final Arguments command) {
        if (command != null) {
            createConfig.put("Cmd", new JSONArray(command.asStrings()));
        }
        return this;
    }

    public ContainerCreateConfig domainname(final String domainname) {
        return add("Domainname", domainname);
    }

    public ContainerCreateConfig entrypoint(final Arguments entrypoint) {
        if (entrypoint != null) {
            createConfig.put("Entrypoint", new JSONArray(entrypoint.asStrings()));
        }
        return this;
    }

    public ContainerCreateConfig environment(final String envPropsFile, final Map<String, String> env, final boolean keepEnvs, final Map mavenProps) throws IllegalArgumentException {

        final Properties envProps = new Properties();
        if (env != null && env.size() > 0) {
            for (final Map.Entry<String, String> entry : env.entrySet()) {
                String value = entry.getValue();
                if (value == null) {
                    value = "";
                }
                envProps.put(entry.getKey(), StrSubstitutor.replace(value, mavenProps));
            }
        }
        if (envPropsFile != null) {
            // Props from external file take precedence
            addPropertiesFromFile(envPropsFile, envProps, keepEnvs);
        }

        if (envProps.size() > 0) {
            addEnvironment(envProps);
        }
        return this;
    }

    public ContainerCreateConfig labels(final Map<String, String> labels) {
        if (labels != null && labels.size() > 0) {
            createConfig.put("Labels", new JSONObject(labels));
        }
        return this;
    }

    public ContainerCreateConfig exposedPorts(final Set<String> portSpecs) {
        if (portSpecs != null && portSpecs.size() > 0) {
            final JSONObject exposedPorts = new JSONObject();
            for (final String portSpec : portSpecs) {
                exposedPorts.put(portSpec, new JSONObject());
            }
            createConfig.put("ExposedPorts", exposedPorts);
        }
        return this;
    }

    public String getImageName() {
        return imageName;
    }

    public ContainerCreateConfig hostname(final String hostname) {
        return add("Hostname", hostname);
    }

    public ContainerCreateConfig user(final String user) {
        return add("User", user);
    }

    public ContainerCreateConfig workingDir(final String workingDir) {
        return add("WorkingDir", workingDir);
    }

    public ContainerCreateConfig hostConfig(final ContainerHostConfig startConfig) {
        return add("HostConfig", startConfig.toJsonObject());
    }

    public ContainerCreateConfig networkingConfig(final ContainerNetworkingConfig networkingConfig) {
        return add("NetworkingConfig", networkingConfig.toJsonObject());
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

    private ContainerCreateConfig add(final String name, final Object value) {
        if (value != null) {
            createConfig.put(name, value);
        }
        return this;
    }

    private String extractContainerPath(final String volume) {
        final String path = EnvUtil.fixupPath(volume);
        if (path.contains(":")) {
            final String[] parts = path.split(":");
            if (parts.length > 1) {
                return parts[1];
            }
        }
        return path;
    }

    private void addEnvironment(final Properties envProps) {
        final JSONArray containerEnv = new JSONArray();
        final Enumeration keys = envProps.keys();
        while (keys.hasMoreElements()) {
            final String key = (String) keys.nextElement();
            String value = envProps.getProperty(key);
            if (value == null) {
                value = "";
            }
            containerEnv.put(key + "=" + value);
        }
        createConfig.put("Env", containerEnv);
    }

    private void addPropertiesFromFile(final String envPropsFile, final Properties envProps, final boolean keepEnvs) {
        // External properties override internally specified properties
        try {
            final FileReader reader = new FileReader(envPropsFile);
            final Properties envPropsFromFile = new Properties();
            envPropsFromFile.load(reader);
            if (keepEnvs) { // remove already set envs
                for (final Object key : envProps.keySet()) {
                    envPropsFromFile.remove(key);
                }
            }
            envProps.putAll(envPropsFromFile);
        } catch (final FileNotFoundException e) {
            throw new IllegalArgumentException(String.format("Cannot find environment property file '%s'", envPropsFile));
        } catch (final IOException e) {
            throw new IllegalArgumentException(String.format("Error while loading environment properties: %s", e.getMessage()), e);
        }
    }
}
