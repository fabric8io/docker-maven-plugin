package org.jolokia.docker.maven.model;

import org.json.JSONArray;
import org.json.JSONObject;

public class ContainersListElement implements Container {

    private final JSONObject json;

    public ContainersListElement(JSONObject json) {
        this.json = json;
    }

    @Override
    public String getName() {
        if (json.has("Names")) {
            JSONArray names = json.getJSONArray("Names");
            for (int i = 0; i < names.length(); i++) {
                String name = names.getString(i);
                if (name.startsWith("/")) {
                    name = name.substring(1);
                }
                if (!name.contains("/")) {
                    return name;
                }
            }
            // this should never happen
            throw new IllegalStateException("Unable to determine container name from 'Names' " + names);
        } else {
            throw new UnsupportedOperationException("Missing 'Names' attribute from a container list element " + json);
        }
    }

    @Override
    public long getCreated() {
        return json.getLong("Created");
    }

    @Override
    public String getId() {
        // only need first 12 to id a container
        return json.getString("Id").substring(0, 12);
    }

    @Override
    public String getImage() {
        return json.getString("Image");
    }

    @Override
    public boolean isRunning() {
        String status = json.getString("Status");
        return status.toLowerCase().contains("up");
    }
}
