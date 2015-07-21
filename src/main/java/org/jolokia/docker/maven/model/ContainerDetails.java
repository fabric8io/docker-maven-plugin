package org.jolokia.docker.maven.model;

import java.util.Calendar;

import javax.xml.bind.DatatypeConverter;

import org.json.JSONObject;

public class ContainerDetails implements Container {

    private final JSONObject json;

    public ContainerDetails(JSONObject json) {
        this.json = json;
    }

    @Override
    public String getName() {
        String name = json.getString("Name");

        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        return name;
    }

    @Override
    public long getCreated() {
        String date = json.getString("Created");
        Calendar cal = DatatypeConverter.parseDateTime(date);
        return cal.getTimeInMillis();
    }

    @Override
    public String getId() {
        // only need first 12 to id a container
        return json.getString("Id").substring(0, 12);
    }

    @Override
    public String getImage() {
        // ID: json.getString("Image");
        return json.getJSONObject("Config").getString("Image");
    }

    @Override
    public boolean isRunning() {
        JSONObject state = json.getJSONObject("State");
        return state.getBoolean("Running");
    }
}
