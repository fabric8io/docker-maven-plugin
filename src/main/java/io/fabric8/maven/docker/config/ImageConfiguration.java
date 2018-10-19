package io.fabric8.maven.docker.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.fabric8.maven.docker.config.build.BuildConfiguration;
import io.fabric8.maven.docker.config.run.RunConfiguration;
import org.apache.commons.lang3.SerializationUtils;

/**
 * @author roland
 * @since 02.09.14
 */
public class ImageConfiguration implements Serializable {

    private String name;

    private String alias;

    private RunConfiguration run;

    private BuildConfiguration build;

    private WatchConfiguration watch;

    private Map<String,String> external;

    private String registry;

    // Used for injection
    public ImageConfiguration() {}

    public String getName() {
        return name;
    }

    /**
     * Override externalConfiguration when defined via special property.
     *
     * @param externalConfiguration Map with alternative config
     */
    public void setExternalConfiguration(Map<String, String> externalConfiguration) {
        this.external = externalConfiguration;
    }

    public String getAlias() {
        return alias;
    }

    public RunConfiguration getRunConfiguration() {
        return (run == null) ? RunConfiguration.DEFAULT : run;
    }

    public BuildConfiguration getBuildConfiguration() {
        return build;
    }

    public WatchConfiguration getWatchConfiguration() {
        return watch;
    }

    public Map<String, String> getExternalConfig() {
        return external;
    }

    public boolean isDataImage() {
        // If there is no explicit run configuration, its a data image
        // TODO: Probably add an explicit property so that a user can indicated whether it
        // is a data image or not on its own.
        return getRunConfiguration() == null;
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

    public String[] validate(NameFormatter nameFormatter) {
        name = nameFormatter.format(name);
        List<String> apiVersions = new ArrayList<>();
        if (build != null) {
            apiVersions.add(build.validate());
        }
        if (run != null) {
            apiVersions.add(run.validate());
        }
        return apiVersions.stream().filter(Objects::nonNull).toArray(String[]::new);
    }

    // =========================================================================
    // Builder for image configurations

    public static class Builder {
        protected ImageConfiguration config;

        public Builder()  {
            this(null);
        }


        public Builder(ImageConfiguration that) {
            if (that == null) {
                this.config = new ImageConfiguration();
            } else {
                this.config = SerializationUtils.clone(that);
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

        public Builder runConfig(RunConfiguration runConfig) {
            config.run = runConfig;
            return this;
        }

        public Builder buildConfig(BuildConfiguration buildConfig) {
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

        public Builder watchConfig(WatchConfiguration watchConfig) {
            config.watch = watchConfig;
            return this;
        }
    }

    // =====================================================================
    /**
     * Format an image name by replacing certain placeholders
     */
    public interface NameFormatter {
        String format(String name);

        NameFormatter IDENTITY = name -> name;
    }


}
