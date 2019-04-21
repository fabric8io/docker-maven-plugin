package io.fabric8.maven.docker.model;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Adapter to convert from JSON representation to model.
 */
public class ImageArchiveManifestEntryAdapter implements ImageArchiveManifestEntry {
    public static final String CONFIG = "Config";
    public static final String REPO_TAGS = "RepoTags";
    public static final String LAYERS = "Layers";
    public static final String CONFIG_JSON_SUFFIX = ".json";

    private String config;
    private List<String> repoTags;
    private List<String> layers;

    public ImageArchiveManifestEntryAdapter(JsonObject json) {
        JsonElement field;

        if((field = json.get(CONFIG)) != null && field.isJsonPrimitive()) {
            this.config = field.getAsString();
        }

        this.repoTags = new ArrayList<>();
        if ((field = json.get(REPO_TAGS)) != null && field.isJsonArray()) {
            for(JsonElement item : field.getAsJsonArray()) {
                if(item.isJsonPrimitive()) {
                    this.repoTags.add(item.getAsString());
                }
            }
        }

        this.layers = new ArrayList<>();
        if ((field = json.get(LAYERS)) != null && field.isJsonArray()) {
            for(JsonElement item : field.getAsJsonArray()) {
                if(item.isJsonPrimitive()) {
                    this.layers.add(item.getAsString());
                }
            }
        }
    }

    @Override
    public String getConfig() {
        return config;
    }

    @Override
    public String getId() {
        return this.config == null || !this.config.endsWith(CONFIG_JSON_SUFFIX) ? this.config : this.config.substring(0, this.config.length() - CONFIG_JSON_SUFFIX.length());
    }

    @Override
    public List<String> getRepoTags() {
        return repoTags;
    }

    @Override
    public List<String> getLayers() {
        return layers;
    }
}
