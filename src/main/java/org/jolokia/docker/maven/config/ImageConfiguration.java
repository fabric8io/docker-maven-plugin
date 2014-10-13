package org.jolokia.docker.maven.config;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author roland
 * @since 02.09.14
 */
public class ImageConfiguration {

    @Parameter(required = true)
    private String name;

    @Parameter
    private RunImageConfiguration run;

    @Parameter
    private BuildImageConfiguration build;

    public String getName() {
        return name;
    }

    public RunImageConfiguration getRunConfiguration() {
        return run;
    }

    public BuildImageConfiguration getBuildConfiguration() {
        return build;
    }
}
