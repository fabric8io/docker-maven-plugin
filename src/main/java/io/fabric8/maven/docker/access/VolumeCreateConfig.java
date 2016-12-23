package io.fabric8.maven.docker.access;

import java.util.Map;

import org.json.JSONObject;

public class VolumeCreateConfig
{
    private final JSONObject createConfig = new JSONObject();

    public VolumeCreateConfig(String name) {
        add("Name", name);
    }

    public VolumeCreateConfig driver(String driver) {
       return add("Driver", driver);
    }

    public VolumeCreateConfig opts(Map<String, String> opts) {
       if (opts != null && opts.size() > 0) {
          add("DriverOpts", new JSONObject(opts));
       }
       return this;
    }

    public VolumeCreateConfig labels(Map<String,String> labels) {
        if (labels != null && labels.size() > 0) {
           add("Labels", new JSONObject(labels));
        }
        return this;
    }

    public String getName() {
        return createConfig.getString("Name");
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

    private VolumeCreateConfig add(String name, Object value) {
        if (value != null) {
            createConfig.put(name, value);
        }
        return this;
    }
}
