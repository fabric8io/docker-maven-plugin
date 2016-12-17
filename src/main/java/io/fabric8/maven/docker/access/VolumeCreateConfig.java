package io.fabric8.maven.docker.access;

import java.io.*;
import java.util.*;

import io.fabric8.maven.docker.util.EnvUtil;
import org.apache.commons.lang3.text.StrSubstitutor;
import io.fabric8.maven.docker.config.Arguments;
import org.json.JSONArray;
import org.json.JSONObject;

public class VolumeCreateConfig 
{
    private final JSONObject createConfig = new JSONObject();
    private final String name;

    public VolumeCreateConfig(String name) 
    {
        this.name = name;
        add("Name", name);
    }

    public VolumeCreateConfig driver(String driver) 
    { return add("Driver", driver); }
    
    public VolumeCreateConfig driverOpts(Map<String, String> driverOpts) 
    { 
       if (driverOpts != null && driverOpts.size() > 0) 
       { add("DriverOpts", new JSONObject(driverOpts)); }
       return this; 
    }
    
    public VolumeCreateConfig labels(Map<String,String> labels) 
    {
        if (labels != null && labels.size() > 0) 
        { add("Labels", new JSONObject(labels)); }
        return this;
    }
    
    public String getVolumeName() { return name; }

    /**
     * Get JSON which is used for <em>creating</em> a volume
     *
     * @return string representation for JSON representing creating a volume
     */
    public String toJson() { return createConfig.toString(); }

    // =======================================================================

    private VolumeCreateConfig add(String name, Object value) 
    {
        if (value != null) { createConfig.put(name, value); }
        return this;
    }

}
