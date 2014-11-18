package org.jolokia.docker.maven.config;

import java.util.*;

import org.apache.maven.plugins.annotations.Parameter;
import org.jolokia.docker.maven.util.EnvUtil;
import org.jolokia.docker.maven.util.StartOrderResolver;

/**
 * @author roland
 * @since 02.09.14
 */
public class ImageConfiguration implements StartOrderResolver.Resolvable {

    @Parameter(required = true)
    private String name;

    @Parameter
    private String alias;

    @Parameter
    private RunImageConfiguration run;

    @Parameter
    private BuildImageConfiguration build;

    @Parameter
    private Map<String,String> reference;


    // Used for injection
    public ImageConfiguration() {}

    // For builder
    private ImageConfiguration(String name, String alias,
                               RunImageConfiguration run, BuildImageConfiguration build,
                               Map<String, String> reference) {
        this.name = name;
        this.alias = alias;
        this.run = run;
        this.build = build;
        this.reference = reference;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getAlias() {
        return alias;
    }

    public RunImageConfiguration getRunConfiguration() {
        return run;
    }

    public BuildImageConfiguration getBuildConfiguration() {
        return build;
    }

    public Map<String, String> getReference() {
        return reference;
    }

    @Override
    public List<String> getDependencies() {
        RunImageConfiguration runConfig = getRunConfiguration();
        List<String> ret = new ArrayList<>();
        if (runConfig != null) {
            addVolumes(runConfig, ret);
            addLinks(runConfig, ret);
        }
        return ret;
    }

    private void addVolumes(RunImageConfiguration runConfig, List<String> ret) {
        if (runConfig.getVolumesFrom() != null) {
            ret.addAll(runConfig.getVolumesFrom());
        }
    }

    private void addLinks(RunImageConfiguration runConfig, List<String> ret) {
        if (runConfig.getLinks() != null) {
            for (String[] link : EnvUtil.splitLinks(runConfig.getLinks())) {
                ret.add(link[0]);
            }
        }
    }

    public boolean isDataImage() {
        // If there is no explicit run configuration, its a data image
        // TODO: Probably add an explicit property so that a user can indicated whether it
        // is a data image or not on its own.
        return getRunConfiguration() == null;
    }

    public String getDescription() {
        return "[" + name + "]" +
               (alias != null ? " \"" + alias + "\"" : "");
    }

    // =========================================================================
    // Builder for image configurations

    public static class Builder {

        String name,alias;
        RunImageConfiguration runConfig;
        BuildImageConfiguration buildConfig;
        Map<String,String> referenceConfig;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder alias(String alias) {
            this.alias = alias;
            return this;
        }

        public Builder runConfig(RunImageConfiguration runConfig) {
            this.runConfig = runConfig;
            return this;
        }

        public Builder buildConfig(BuildImageConfiguration buildConfig) {
            this.buildConfig = buildConfig;
            return this;
        }

        public Builder referenceConfig(Map<String,String> referenceConfig) {
            this.referenceConfig = referenceConfig;
            return this;
        }

        public ImageConfiguration build() {
            return new ImageConfiguration(name,alias,runConfig,buildConfig,referenceConfig);
        }
    }
}
