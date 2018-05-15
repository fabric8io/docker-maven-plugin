package io.fabric8.maven.docker.config;

import java.io.Serializable;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

public class DockerMachineConfiguration implements Serializable {

    public static final String DOCKER_MACHINE_NAME_PROP = "docker.machine.name";
    public static final String DOCKER_MACHINE_AUTO_CREATE_PROP = "docker.machine.autoCreate";
    public static final String DOCKER_MACHINE_REGENERATE_CERTS_AFTER_START_PROP = "docker.machine.regenerateCertsAfterStart";

    /**
     * Name of the docker-machine
     */
    @Parameter
    private String name = "default";

    /**
     * Should the docker-machine be created if it does not exist?
     */
    @Parameter
    private Boolean autoCreate = Boolean.FALSE;

    /**
     * Should the docker-machine's certificates be regenerated after starting?
     */
    @Parameter
    private Boolean regenerateCertsAfterStart = Boolean.FALSE;

    /**
     * When creating a docker-machine, the map of createOptions for the driver.
     * Do not include the '--' portion of the option name.  For options without values, leave the value text empty.
     * e.g. --virtualbox-cpu-count 1 --virtualbox-no-share would be written as:<code>
     * &lt;virtualbox-cpu-count&gt;1&lt;/virtualbox-cpu-count&gt;
     * &lt;virtualbox-no-share/&gt;
     * </code>
     */
    @Parameter
    private Map<String, String> createOptions;

    public DockerMachineConfiguration() {}

    public DockerMachineConfiguration(String name, String autoCreate, String regenerateCertsAfterStart) {
        this.name = name;
        this.autoCreate = autoCreate != null ? Boolean.parseBoolean(autoCreate) : Boolean.FALSE;
        this.regenerateCertsAfterStart = regenerateCertsAfterStart != null ? Boolean.parseBoolean(regenerateCertsAfterStart) : Boolean.FALSE;
    }

    public String getName() {
        return name;
    }

    public Boolean getAutoCreate() {
        return autoCreate;
    }

    public Boolean getRegenerateCertsAfterStart() {
        return regenerateCertsAfterStart;
    }

    public Map<String, String> getCreateOptions() {
        return createOptions;
    }

    @Override
    public String toString() {
        return "MachineConfiguration [name=" + name + ", autoCreate=" + autoCreate + ",regenerateCertsAfterStart=" + regenerateCertsAfterStart + ", createOptions=" + createOptions + "]";
    }
}
