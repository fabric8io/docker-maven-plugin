package io.fabric8.maven.docker.model;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.util.*;

/**
 *  Describes the Details of a created Volume
 *  
 *  @author Tom Burton
 *  @version Dec 16, 2016
 */
public class VolumeDetails implements Volume 
{
    static final String LABELS = "Labels";
    static final String NAME = "Name";
    static final String DRIVER = "Driver";
    static final String MOUNTPOINT = "Mountpount";
    static final String STATUS = "Status";
    static final String SCOPE = "Scope";
    
    static final String SLASH = "/";
    
    //TODO: should these be an enum?
    static final String SCOPE_GLOBAL = "global";
    static final String SCOPE_LOCAL  = "local";

    private final JSONObject json;

    public VolumeDetails(JSONObject json) { this.json = json; }

    @Override
    public String getName() 
    {
       String name = json.getString(NAME);

       if (name.startsWith(SLASH)) { name = name.substring(1); }
       return name;
    }


    @Override
    public String getDriver() { return json.getString(DRIVER); }

    @Override
    public String getMountpoint() { return json.getString(MOUNTPOINT); }

    @Override
    public Map<String, String> getStatus() 
    { return parseMap(json.getJSONObject(STATUS)); }

    @Override
    public Map<String, String> getLabels() 
    { return parseMap(json.getJSONObject(LABELS)); }
      
    private Map<String, String> parseMap(JSONObject labels) 
    {
        int length = labels.length();
        Map<String, String> mapped = new HashMap<>(length);

        Iterator<String> iterator = labels.keys();
        while (iterator.hasNext()) 
        {
           String key = iterator.next();
           mapped.put(key, labels.get(key).toString());
        }

        return mapped;
    }

   @Override
   public String getScope() { return json.getString(SCOPE); }
    
}
