package io.fabric8.maven.docker.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ImageArchiveManifestAdapter implements ImageArchiveManifest {
    private List<ImageArchiveManifestEntry> entries;

    private Map<String, JsonObject> config;

    public ImageArchiveManifestAdapter(JsonElement json) {
        this.entries = new ArrayList<>();

        if(json.isJsonArray()) {
            for(JsonElement entryJson : json.getAsJsonArray()) {
                if(entryJson.isJsonObject()) {
                    this.entries.add(new ImageArchiveManifestEntryAdapter(entryJson.getAsJsonObject()));
                }
            }
        }

        this.config = new LinkedHashMap<>();
    }

    @Override
    public List<ImageArchiveManifestEntry> getEntries() {
        return this.entries;
    }

    @Override
    public JsonObject getConfig(String configName) {
        return this.config.get(configName);
    }

    public JsonObject putConfig(String configName, JsonObject config) {
        return this.config.put(configName, config);
    }
}
