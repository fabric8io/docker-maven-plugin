package org.jolokia.docker.maven.model;

import org.json.JSONObject;

public class ContainerDetails {

    private final JSONObject json;

    public ContainerDetails(String json) {
        this.json = new JSONObject(json);
    }

    public String getName() {
        String name = json.getString("Name");

        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        
        return name;
    }
    
    public State getState() {
        return new State(json.getJSONObject("State"));
    }

    public class State {
        private final JSONObject json;

        State(JSONObject json) {
            this.json = json;
        }

        public boolean isRunning() {
            return json.getBoolean("Running");
        }
    }
}
