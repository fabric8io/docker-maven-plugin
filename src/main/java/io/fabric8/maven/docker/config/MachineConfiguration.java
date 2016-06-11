package io.fabric8.maven.docker.config;

import java.util.Map;

import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Configuration;

@Component(role = MachineConfiguration.class, instantiationStrategy = "per-lookup")
public class MachineConfiguration {

    /**
     * Name of the docker-machine
     * @parameter expression="${docker.machine.name}" default-value="default"
     */
    @Configuration("${docker.machine.name}")
    private String name;

    /**
     * Should the docker-machine be created if it does not exist?
     * @parameter expression="${docker.machine.autoCreate}" default-value="false"
     */
    @Configuration("${docker.machine.autoCreate}")
    private Boolean autoCreate;

    /**
     * When creating a docker-machine, the map of createOptions for the driver.
     * Do not include the '--' portion of the option name.  For options without values, leave the value text empty.
     * e.g. --virtualbox-cpu-count 1 --virtualbox-no-share would be written as:<code>
     * &lt;virtualbox-cpu-count&gt;1&lt;/virtualbox-cpu-count&gt;
     * &lt;virtualbox-no-share/&gt;
     * </code>
     */
    private Map<String, String> createOptions;

    private boolean isDefaults = true;
    private Map<String, String> env;
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getAutoCreate() {
        return autoCreate;
    }

    public void setAutoCreate(Boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    public Map<String, String> getCreateOptions() {
        return createOptions;
    }

    public void setCreateOptions(Map<String, String> createOptions) {
        this.createOptions = createOptions;
    }

    public Map<String, String> getEnv() {
		return env;
	}

    public void setEnv(Map<String, String> env) {
		this.env = env;
	}

    @Override
    public String toString() {
        return "MachineConfiguration [name=" + name + ", autoCreate=" + autoCreate + ", createOptions=" + createOptions + "]";
    }

    public boolean resolveDefines(PluginParameterExpressionEvaluator evaluator, Log log) {
        name = resolveProperty(evaluator, log, String.class, name, "${docker.machine.name}", "default");
        autoCreate = resolveProperty(evaluator, log, Boolean.class, autoCreate, "${docker.machine.autoCreate}", Boolean.FALSE);
        return isDefaults;
    }

    @SuppressWarnings("unchecked")
    private <T> T resolveProperty(PluginParameterExpressionEvaluator evaluator, Log log,
            Class<T> type, T value, String expression, T defaultValue) {
        if (value != null) {
        	isDefaults = false;
            return value;
        }
        try {
            value = (T) evaluator.evaluate(expression, type);
            if (value != null) {
                if (!type.isInstance(value)) {
                    String s = (String) value;
                    return (T) (s.isEmpty() ? Boolean.TRUE : Boolean.valueOf(s));
                }
                isDefaults = false;
                return value;
            }
        } catch (Exception ex) {
            log.error(ex);
        }
        return defaultValue;
    }
}