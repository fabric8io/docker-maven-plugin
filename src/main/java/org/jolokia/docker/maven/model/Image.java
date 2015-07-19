package org.jolokia.docker.maven.model;

import java.util.ArrayList;
import java.util.List;

import edu.emory.mathcs.backport.java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Wrapper around a JSON return object representing an image
 *
 */
public class Image {

    private final JSONObject json;

    /**
     * Constructor wrapping the JSON image object
     *
     * @param json initial json response
     */
    public Image(JSONObject json) {
        this.json = json;
    }

    /**
     *
     * @return the image id
     */
    public String getId() {
        return json.getString("Id").substring(0, 12);
    }

    /**
     * Get all repo tags as an unmodifiable list
     *
     * @return repo tags
     */
    public List<String> getRepoTags() {
        JSONArray array = json.getJSONArray("RepoTags");
        List<String> tags = new ArrayList<>(array.length());
        
        for (int i = 0; i < array.length(); i++) {
            tags.add(array.getString(i));
        }

        return Collections.unmodifiableList(tags);
    }


}
