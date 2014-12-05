package org.jolokia.docker.maven.config;

import java.util.*;

import org.jolokia.docker.maven.util.EnvUtil;
import org.jolokia.docker.maven.util.StartOrderResolver;

/**
 * @author roland
 * @since 02.09.14
 */
public class ImageConfiguration implements StartOrderResolver.Resolvable {

    /**
     * @parameter
     * @required
     */
    private String name;

    /**
     * @parameter
     */
    private String alias;

    /**
     * @parameter
     */
    private RunImageConfiguration run;

    /**
     * @parameter
     */
    private BuildImageConfiguration build;

    /**
     * @parameter
     */
    private Map<String,String> external;

    // Used for injection
    public ImageConfiguration() {}

    // For builder
    private ImageConfiguration(String name, String alias,
                               RunImageConfiguration run, BuildImageConfiguration build,
                               Map<String, String> external) {
        this.name = name;
        this.alias = alias;
        this.run = run;
        this.build = build;
        this.external = external;
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

    public Map<String, String> getExternalConfig() {
        return external;
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
            for (String[] link : EnvUtil.splitOnLastColon(runConfig.getLinks())) {
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
        Map<String,String> externalConfig;

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

        public Builder externalConfig(Map<String, String> externalConfig) {
            this.externalConfig = externalConfig;
            return this;
        }

        public ImageConfiguration build() {
            return new ImageConfiguration(name,alias,runConfig,buildConfig, externalConfig);
        }
    }
}
