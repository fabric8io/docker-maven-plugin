package io.fabric8.maven.docker.access;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.google.gson.JsonPrimitive;
import io.fabric8.maven.docker.config.HealthCheckConfiguration;
import io.fabric8.maven.docker.config.HealthCheckMode;
import org.apache.commons.text.StrSubstitutor;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.util.JsonFactory;

import static io.fabric8.maven.docker.access.util.ComposeDurationUtil.goDurationToNanoseconds;

public class ContainerCreateConfig {

    private final JsonObject createConfig = new JsonObject();
    private final String imageName;
    private final String platform;

    public ContainerCreateConfig(String imageName, String platform) {
        this.imageName = imageName;
        this.platform = platform;
        createConfig.addProperty("Image", imageName);
    }

    public ContainerCreateConfig(String imageName) {
        this(imageName, null);
    }

    public ContainerCreateConfig binds(List<String> volumes) {
        if (volumes != null && !volumes.isEmpty()) {
            JsonObject extractedVolumes = new JsonObject();

            for (String volume : volumes) {
                extractedVolumes.add(extractContainerPath(volume),
                                     new JsonObject());
            }
            createConfig.add("Volumes", extractedVolumes);
        }
        return this;
    }

    public ContainerCreateConfig command(Arguments command) {
        if (command != null) {
            createConfig.add("Cmd", JsonFactory.newJsonArray(command.asStrings()));
        }
        return this;
    }

    public ContainerCreateConfig domainname(String domainname) {
        return add("Domainname", domainname);
    }

    public ContainerCreateConfig entrypoint(Arguments entrypoint) {
        if (entrypoint != null) {
            createConfig.add("Entrypoint", JsonFactory.newJsonArray(entrypoint.asStrings()));
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
                } else if (value.matches("^\\+\\$\\{.*}$")) {
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

    public ContainerCreateConfig labels(Map<String, String> labels) {
        if (labels != null && labels.size() > 0) {
            createConfig.add("Labels", JsonFactory.newJsonObject(labels));
        }
        return this;
    }

    public ContainerCreateConfig exposedPorts(Set<String> portSpecs) {
        if (portSpecs != null && portSpecs.size() > 0) {
            JsonObject exposedPorts = new JsonObject();
            for (String portSpec : portSpecs) {
                exposedPorts.add(portSpec, new JsonObject());
            }
            createConfig.add("ExposedPorts", exposedPorts);
        }
        return this;
    }

    public ContainerCreateConfig healthcheck(HealthCheckConfiguration healthCheckConfiguration) {
        if (healthCheckConfiguration != null) {
            JsonObject healthcheck = new JsonObject();
            if (healthCheckConfiguration.getCmd() != null) {
                healthcheck.add("Test", JsonFactory.newJsonArray(healthCheckConfiguration.getCmd().asStrings()));
            }
            if (healthCheckConfiguration.getMode() != HealthCheckMode.none) {
                if (healthCheckConfiguration.getRetries() != null) {
                    healthcheck.add("Retries", new JsonPrimitive(healthCheckConfiguration.getRetries()));
                }
                if (healthCheckConfiguration.getInterval() != null) {
                    String intervalValue = healthCheckConfiguration.getInterval();
                    String field = "Interval";
                    healthcheck.add(field, new JsonPrimitive(goDurationToNanoseconds(intervalValue, field)));
                }
                if (healthCheckConfiguration.getStartPeriod() != null) {
                    String field = "StartPeriod";
                    String intervalValue = healthCheckConfiguration.getStartPeriod();
                    healthcheck.add(field, new JsonPrimitive(goDurationToNanoseconds(intervalValue, field)));
                }
                if (healthCheckConfiguration.getTimeout() != null) {
                    String field = "Timeout";
                    String intervalValue = healthCheckConfiguration.getTimeout();
                    healthcheck.add(field, new JsonPrimitive(goDurationToNanoseconds(intervalValue, field)));
                }
            }
            createConfig.add("Healthcheck", healthcheck);
        }
        return this;
    }

    public String getImageName() {
        return imageName;
    }

    public String getPlatform() {
        return platform;
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

    private ContainerCreateConfig add(String name, String value) {
        if (value != null) {
            createConfig.addProperty(name, value);
        }
        return this;
    }

    private ContainerCreateConfig add(String name, JsonObject value) {
        if (value != null) {
            createConfig.add(name, value);
        }
        return this;
    }

    private String extractContainerPath(String volume) {
        if (volume.contains(":")) {
            // only split when : is not followed by \ (Windows)
            String[] parts = volume.split(":(?!\\\\)");
            if (parts.length > 1) {
                return parts[1];
            }
        }
        return volume;
    }

    private void addEnvironment(Properties envProps) {
        JsonArray containerEnv = new JsonArray();
        Enumeration keys = envProps.keys();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            String value = envProps.getProperty(key);
            if (value == null) {
                value = "";
            }
            containerEnv.add(key + "=" + value);
        }
        createConfig.add("Env", containerEnv);
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
