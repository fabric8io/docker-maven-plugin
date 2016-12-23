package io.fabric8.maven.docker.config;

import java.io.Serializable;
import java.util.*;

import io.fabric8.maven.docker.util.*;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author roland
 * @since 02.09.14
 */
public class ImageConfiguration implements StartOrderResolver.Resolvable, Serializable {

    @Parameter(required = true)
    private String name;

    @Parameter
    private String alias;

    @Parameter
    private RunImageConfiguration run;

    @Parameter
    private BuildImageConfiguration build;

    @Parameter
    private WatchImageConfiguration watch;

    @Parameter
    private Map<String,String> external;

    @Parameter
    private String registry;

    // Used for injection
    public ImageConfiguration() {}

    @Override
    public String getName() {
        return name;
    }

    /**
     * Change the name which can be useful in long running runs e.g. for updating
     * images when doing updates. Use with caution and only for those circumstances.
     *
     * @param name image name to set.
     */
    public void setName(String name) {
        this.name = name;
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
            addContainerNetwork(runConfig, ret);
            addDependsOn(runConfig, ret);
        }
        return ret;
    }

    private void addVolumes(RunImageConfiguration runConfig, List<String> ret) {
        RunVolumeConfiguration volConfig = runConfig.getVolumeConfiguration();
        if (volConfig != null) {
            List<String> volumeImages = volConfig.getFrom();
            if (volumeImages != null) {
                ret.addAll(volumeImages);
            }
        }
    }

    private void addLinks(RunImageConfiguration runConfig, List<String> ret) {
        // Custom networks can have circular links, no need to be considered for the starting order.
        if (runConfig.getLinks() != null && !runConfig.getNetworkingConfig().isCustomNetwork()) {
            for (String[] link : EnvUtil.splitOnLastColon(runConfig.getLinks())) {
                ret.add(link[0]);
            }
        }
    }

    private void addContainerNetwork(RunImageConfiguration runConfig, List<String> ret) {
        NetworkConfig config = runConfig.getNetworkingConfig();
        String alias = config.getContainerAlias();
        if (alias != null) {
            ret.add(alias);
        }
    }

    private void addDependsOn(RunImageConfiguration runConfig, List<String> ret) {
        // Only used in custom networks.
        if (runConfig.getDependsOn() != null && runConfig.getNetworkingConfig().isCustomNetwork()) {
            for (String link : runConfig.getDependsOn()) {
                ret.add(link);
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
        return String.format("[%s] %s", name, (alias != null ? "\"" + alias + "\"" : "")).trim();
    }

    public String getRegistry() {
        return registry;
    }

    @Override
    public String toString() {
        return String.format("ImageConfiguration {name='%s', alias='%s'}", name, alias);
    }

    public String initAndValidate(ConfigHelper.NameFormatter nameFormatter, Logger log) {
        name = nameFormatter.format(name);
        String minimalApiVersion = null;
        if (build != null) {
            minimalApiVersion = build.initAndValidate(log);
        }
        if (run != null) {
            minimalApiVersion = EnvUtil.extractLargerVersion(minimalApiVersion, run.initAndValidate());
        }
        return minimalApiVersion;
    }

    // =========================================================================
    // Builder for image configurations

    public static class Builder {
        private final ImageConfiguration config;

        public Builder()  {
            this(null);
        }


        public Builder(ImageConfiguration that) {
            if (that == null) {
                this.config = new ImageConfiguration();
            } else {
                this.config = DeepCopy.copy(that);
            }
        }

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

        public ImageConfiguration build() {
            return config;
        }

        public Builder watchConfig(WatchImageConfiguration watchConfig) {
            config.watch = watchConfig;
            return this;
        }
    }
}
