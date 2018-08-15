package io.fabric8.maven.docker.access;

import org.apache.commons.text.StrSubstitutor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.util.EnvUtil;

import static io.fabric8.maven.docker.util.JsonUtils.put;

public class ContainerCreateConfig {

    private final JSONObject createConfig = new JSONObject();
    private final String imageName;
    public ContainerCreateConfig(String imageName) {
        this.imageName = imageName;
        put(createConfig, "Image", imageName);
    }

    public ContainerCreateConfig binds(List<String> volumes) {
        if (volumes != null && !volumes.isEmpty()) {
            JSONObject extractedVolumes = new JSONObject();

            for (String volume : volumes) {
                put(extractedVolumes, extractContainerPath(volume),
                        new JSONObject());
            }
            put(createConfig, "Volumes", extractedVolumes);
        }
        return this;
    }

    public ContainerCreateConfig command(Arguments command) {
        if (command != null) {
            put(createConfig, "Cmd", new JSONArray(command.asStrings()));
        }
        return this;
    }

    public ContainerCreateConfig domainname(String domainname) {
        return add("Domainname", domainname);
    }

    public ContainerCreateConfig entrypoint(Arguments entrypoint) {
        if (entrypoint != null) {
            put(createConfig, "Entrypoint", new JSONArray(entrypoint.asStrings()));
        }
        return this;
    }

    public ContainerCreateConfig environment(String envPropsFile, Map<String, String> env, Map mavenProps) throws IllegalArgumentException {

        Properties envProps = new Properties();
        if (env != null && env.size() > 0) {
            for (Map.Entry<String, String> entry : env.entrySet()) {
                String value = entry.getValue();
                if (value == null) {
                    value = "";
                } else if(value.matches("^\\+\\$\\{.*}$")) {
                    /*
                     * This case is to handle the Maven interpolation issue which used
                     * to occur when using ${..} only without any suffix.
                     */
                    value = value.substring(1, value.length());
                }
                envProps.put(entry.getKey(), StrSubstitutor.replace(value, mavenProps));
            }
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

    public ContainerCreateConfig labels(Map<String,String> labels) {
        if (labels != null && labels.size() > 0) {
            put(createConfig, "Labels", new JSONObject(labels));
        }
        return this;
    }

    public ContainerCreateConfig exposedPorts(Set<String> portSpecs) {
        if (portSpecs != null && portSpecs.size() > 0) {
            JSONObject exposedPorts = new JSONObject();
            for (String portSpec : portSpecs) {
                put(exposedPorts, portSpec, new JSONObject());
            }
            put(createConfig, "ExposedPorts", exposedPorts);
        }
        return this;
    }

    public String getImageName() {
        return imageName;
    }

    public ContainerCreateConfig hostname(String hostname) {
        return add("Hostname", hostname);
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

    public ContainerCreateConfig networkingConfig(ContainerNetworkingConfig networkingConfig) {
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

    private ContainerCreateConfig add(String name, Object value) {
        if (value != null) {
            put(createConfig, name, value);
        }
        return this;
    }

    private String extractContainerPath(String volume) {
        String path  = EnvUtil.fixupPath(volume);
        if (path.contains(":")) {
            String[] parts = path.split(":");
            if (parts.length > 1) {
                return parts[1];
            }
        }
        return path;
    }

    private void addEnvironment(Properties envProps) {
        JSONArray containerEnv = new JSONArray();
        Enumeration keys = envProps.keys();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            String value = envProps.getProperty(key);
            if (value == null) {
                value = "";
            }
            containerEnv.put(key + "=" + value);
        }
        put(createConfig, "Env", containerEnv);
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
