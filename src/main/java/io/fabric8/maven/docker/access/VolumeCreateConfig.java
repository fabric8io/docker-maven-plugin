package io.fabric8.maven.docker.access;

import com.google.gson.JsonObject;

import java.util.Map;

import io.fabric8.maven.docker.util.GsonBridge;

public class VolumeCreateConfig
{
    private final JsonObject createConfig = new JsonObject();

    public VolumeCreateConfig(String name) {
        add("Name", name);
    }

    public VolumeCreateConfig driver(String driver) {
       return add("Driver", driver);
    }

    public VolumeCreateConfig opts(Map<String, String> opts) {
       if (opts != null && opts.size() > 0) {
          add("DriverOpts", GsonBridge.toJsonObject(opts));
       }
       return this;
    }

    public VolumeCreateConfig labels(Map<String,String> labels) {
        if (labels != null && labels.size() > 0) {
           add("Labels", GsonBridge.toJsonObject(labels));
        }
        return this;
    }

    public String getName() {
        return createConfig.get("Name").getAsString();
    }

    /**
     * Get JSON which is used for <em>creating</em> a volume
     *
     * @return string representation for JSON representing creating a volume
     */
    public String toJson() {
        return createConfig.toString();
    }

    // =======================================================================

    private VolumeCreateConfig add(String name, JsonObject value) {
        if (value != null) {
            createConfig.add(name, value);
        }
        return this;
    }

    private VolumeCreateConfig add(String name, String value) {
        if (value != null) {
            createConfig.addProperty(name, value);
        }
        return this;
    }
}
