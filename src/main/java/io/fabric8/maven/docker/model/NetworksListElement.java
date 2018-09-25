package io.fabric8.maven.docker.model;

import com.google.gson.JsonObject;

public class NetworksListElement implements Network {

    static final String NAME = "Name";
    static final String ID = "Id";
    static final String SCOPE = "Scope";
    static final String DRIVER = "Driver";

    private final JsonObject json;

    public NetworksListElement(JsonObject json) {
        this.json = json;
    }

    @Override
    public String getName() {
        return json.get(NAME).getAsString();
    }

    @Override
    public String getDriver() {
        return json.get(DRIVER).getAsString();
    }

    @Override
    public String getScope() {
        return json.get(SCOPE).getAsString();
    }

    @Override
    public String getId() {
        // only need first 12 to id a network
        return json.get(ID).getAsString().substring(0, 12);
    }

}
