package io.fabric8.maven.docker.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.fabric8.maven.docker.util.DeepCopy;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.StartOrderResolver;
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
    private String stopNamePattern;

    @Parameter
    private String removeNamePattern;

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

    /**
     * Override externalConfiguration when defined via special property.
     *
     * @param externalConfiguration Map with alternative config
     */
    public void setExternalConfiguration(Map<String, String> externalConfiguration) {
        this.external = externalConfiguration;
    }

    @Override
	public String getAlias() {
        return alias;
    }

    public String getStopNamePattern() {
        return stopNamePattern;
    }

    public String getRemoveNamePattern() {
        return removeNamePattern;
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
        if (!runConfig.getNetworkingConfig().isCustomNetwork()) {
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
        if (runConfig.getNetworkingConfig().isCustomNetwork()) {
            ret.addAll(runConfig.getDependsOn());
        }
    }

    public boolean isDataImage() {
        // If there is no explicit run configuration, its a data image
        // TODO: Probably add an explicit property so that a user can indicated whether it
        // is a data image or not on its own.
        return run == null;
    }

    public String getDescription() {
        return String.format("[%s] %s", new ImageName(name).getFullName(), (alias != null ? "\"" + alias + "\"" : "")).trim();
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

        public Builder removeNamePattern(String removeNamePattern) {
            config.removeNamePattern = removeNamePattern;
            return this;
        }

        public Builder stopNamePattern(String stopNamePattern) {
            config.stopNamePattern = stopNamePattern;
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

        public Builder registry(String registry) {
            config.registry = registry;
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
