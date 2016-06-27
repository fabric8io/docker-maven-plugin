package io.fabric8.maven.docker.config;

import java.util.Map;

import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Configuration;

public class DockerMachineConfiguration {

    public static final String DOCKER_MACHINE_NAME_PROP = "docker.machine.name";
    public static final String DOCKER_MACHINE_AUTO_CREATE_PROP = "docker.machine.autoCreate";
    
    /**
     * Name of the docker-machine
     * @parameter expression="${docker.machine.name}" default-value="default"
     */
    @Parameter
    private String name;

    /**
     * Should the docker-machine be created if it does not exist?
     * @parameter expression="${docker.machine.autoCreate}" default-value="false"
     */
    @Parameter
    private Boolean autoCreate;

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

    public DockerMachineConfiguration(String name, String autoCreate) {
        this.name = name;
        this.autoCreate = autoCreate != null ? Boolean.parseBoolean(autoCreate) : Boolean.FALSE;
    }

    public String getName() {
        return name;
    }

    public Boolean getAutoCreate() {
        return autoCreate;
    }

    public Map<String, String> getCreateOptions() {
        return createOptions;
    }

    @Override
    public String toString() {
        return "MachineConfiguration [name=" + name + ", autoCreate=" + autoCreate + ", createOptions=" + createOptions + "]";
    }
}