package io.fabric8.maven.docker.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ImageDetails implements Image {
    public static final String CREATED = "Created";
    public static final String ID = "Id";
    public static final String LABELS = "Labels";
    public static final String PARENT_ID = "ParentId";
    public static final String REPO_TAGS = "RepoTags";
    public static final String REPO_DIGESTS = "RepoDigests";
    public static final String SIZE = "Size";
    public static final String VIRTUAL_SIZE = "VirtualSize";

    private final JsonObject json;

    public ImageDetails(JsonObject json) {
        this.json = json;
    }

    @Override
    public String getId() {
        return json.get(ID).getAsString();
    }

    @Override
    public String getParentId() {
        return json.has(PARENT_ID) ? json.get(PARENT_ID).getAsString() : null;
    }

    @Override
    public long getCreated() {
        return json.get(CREATED).getAsLong();
    }

    @Override
    public long getSize() {
        return json.get(SIZE).getAsLong();
    }

    @Override
    public long getVirtualSize() {
        return json.get(VIRTUAL_SIZE).getAsLong();
    }

    @Override
    public Map<String, String> getLabels() {
        return json.has(LABELS) ?
                mapLabels(json.getAsJsonObject(LABELS)) :
                Collections.emptyMap();
    }

    @Override
    public List<String> getRepoTags() {
        List<String> repoTags = new ArrayList<>();

        if (json.has(REPO_TAGS)) {
            for(JsonElement item : json.getAsJsonArray(REPO_TAGS)) {
                repoTags.add(item.getAsString());
            }
        }

        return repoTags;
    }

    @Override
    public List<String> getRepoDigests() {
        List<String> repoDigests = new ArrayList<>();

        if (json.has(REPO_DIGESTS)) {
            for(JsonElement item : json.getAsJsonArray(REPO_DIGESTS)) {
                repoDigests.add(item.getAsString());
            }
        }

        return repoDigests;
    }

    private Map<String, String> mapLabels(JsonObject labels) {
        int length = labels.size();
        Map<String, String> mapped = new LinkedHashMap<>(length);

        Iterator<String> iterator = labels.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            mapped.put(key, labels.get(key).getAsString());
        }

        return mapped;
    }
}
