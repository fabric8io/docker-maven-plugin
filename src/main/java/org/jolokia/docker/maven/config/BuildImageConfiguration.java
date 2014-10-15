package org.jolokia.docker.maven.config;

import java.util.List;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author roland
 * @since 02.09.14
 */
public class BuildImageConfiguration {

    // Base Image name of the data image to use.
    @Parameter
    private String from;

    @Parameter
    private String exportDir;

    @Parameter
    private String registry;

    @Parameter
    private String assemblyDescriptor;

    @Parameter
    private String assemblyDescriptorRef;

    @Parameter
    private List<String> ports;

    @Parameter
    private Map<String,String> env;

    @Parameter(defaultValue = "true")
    private boolean doNotPush;

    public String getFrom() {
        return from;
    }

    public String getExportDir() {
        return exportDir;
    }

    public String getRegistry() {
        return registry;
    }

    public String getAssemblyDescriptor() {
        return assemblyDescriptor;
    }

    public String getAssemblyDescriptorRef() {
        return assemblyDescriptorRef;
    }

    public List<String> getPorts() {
        return ports;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public boolean isDoNotPush() {
        return doNotPush;
    }
}
