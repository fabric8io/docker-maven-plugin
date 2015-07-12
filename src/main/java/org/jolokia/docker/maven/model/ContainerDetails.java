package org.jolokia.docker.maven.model;

import java.util.Calendar;

import javax.xml.bind.DatatypeConverter;

import org.json.JSONObject;

public class ContainerDetails implements Container {

    private final JSONObject json;

    public ContainerDetails(JSONObject json) {
        this.json = json;
    }

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
        return json.getString("Id");
    }

    @Override
    public String getImage() {
        // ID: json.getString("Image");
        return json.getJSONObject("Config").getString("Image");
    }

    public boolean isRunning() {
        JSONObject state = json.getJSONObject("State");
        return state.getBoolean("Running");
    }
}
