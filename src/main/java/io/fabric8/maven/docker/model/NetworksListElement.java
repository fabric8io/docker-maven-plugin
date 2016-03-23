package io.fabric8.maven.docker.model;

import org.json.JSONObject;

public class NetworksListElement implements Network {

    static final String NAME = "Name";
    static final String ID = "Id";
    static final String SCOPE = "Scope";
    static final String DRIVER = "Driver";

    private final JSONObject json;

    public NetworksListElement(JSONObject json) {
        this.json = json;
    }

    @Override
    public String getName() {
        return json.getString(NAME);
    }

    @Override
    public String getDriver() {
        return json.getString(DRIVER);
    }

    @Override
    public String getScope() {
        return json.getString(SCOPE);
    }

    @Override
    public String getId() {
        // only need first 12 to id a network
        return json.getString(ID).substring(0, 12);
    }

}
