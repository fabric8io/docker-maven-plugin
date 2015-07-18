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
    private WatchImageConfiguration watch;

    /**
     * @parameter
     */
    private Map<String,String> external;

    /**
     * @parameter
     */
    private String registry;

    /**
     * @parameter default-value="both"
     */
    private BuildRunMode mode;
    
    // Used for injection
    public ImageConfiguration() {}
   
    @Override
    public String getName() {
        return name;
    }

    @Override
	public String getAlias() {
        return alias;
    }

    public RunImageConfiguration getRunConfiguration() {
        return (run == null) ? RunImageConfiguration.DEFAULT : run;
    }

    public BuildImageConfiguration getBuildConfiguration() {
        return build;
    }

    public WatchImageConfiguration getWatchConfiguration() {
        return watch;
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
        VolumeConfiguration volConfig = runConfig.getVolumeConfiguration();
        if (volConfig != null) {
            List<String> volumeImages = volConfig.getFrom();
            if (volumeImages != null) {
                ret.addAll(volumeImages);
            }
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

    public BuildRunMode getBuildRunMode() {
        return (mode == null) ? BuildRunMode.both : mode;
    }
    
    public String getDescription() {
        return String.format("[%s] %s", name, (alias != null ? "\"" + alias + "\"" : ""));
    }

    public String getRegistry() {
        return registry;
    }

    @Override
    public String toString() {
        return String.format("ImageConfiguration {name='%s', alias='%s'}", name, alias);
    }

    // =========================================================================
    // Builder for image configurations

    public static class Builder {
        private ImageConfiguration config = new ImageConfiguration();

        public Builder name(String name) {
            config.name = name;
            return this;
        }

        public Builder alias(String alias) {
            config.alias = alias;
            return this;
        }

        public Builder runConfig(RunImageConfiguration runConfig) {
            config.run = runConfig;
            return this;
        }

        public Builder buildConfig(BuildImageConfiguration buildConfig) {
            config.build = buildConfig;
            return this;
        }

        public Builder externalConfig(Map<String, String> externalConfig) {
            config.external = externalConfig;
            return this;
        }

        public Builder buildRunMode(String mode) {
            config.mode = (mode == null) ? BuildRunMode.both : BuildRunMode.valueOf(mode);
            return this;
        }
        
        public ImageConfiguration build() {
            return config;
        }

        public Builder watchConfig(WatchImageConfiguration watchConfig) {
            config.watch = watchConfig;
            return this;
        }
    }
}
