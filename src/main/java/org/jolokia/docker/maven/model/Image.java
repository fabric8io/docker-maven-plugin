package org.jolokia.docker.maven.model;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class Image {

    private final JSONObject json;
   
    public Image(JSONObject json) {
        this.json = json;
    }
    
    public Image(String json) {
       this(new JSONObject(json));
    }
    
    public String getId() {
        return json.getString("Id");
    }

    public List<String> getRepoTags() {
        JSONArray array = json.getJSONArray("RepoTags");
        List<String> tags = new ArrayList<>(array.length());
        
        for (int i = 0; i < array.length(); i++) {
            tags.add(array.getString(i));
        }

        return tags;
    }    
}
