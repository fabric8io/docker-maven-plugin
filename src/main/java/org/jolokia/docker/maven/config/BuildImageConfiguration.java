package org.jolokia.docker.maven.config;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author roland
 * @since 02.09.14
 */
public class BuildImageConfiguration {

    // Base Image name of the data image to use.
    @Parameter(required = false, defaultValue = "busybox")
    private String baseImage;

    @Parameter(required = false, defaultValue = "/maven")
    private String exportDir;

    @Parameter(required = false)
    private String registry;

    @Parameter(required = false)
    private String assemblyDescriptor;

    @Parameter(required = false)
    private String assemblyDescriptorRef;


    public String getBaseImage() {
        return baseImage;
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
}
