package org.jolokia.docker.maven.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class Container {

    private final JSONObject json;

    public Container(JSONObject json) {
        this.json = json;
    }

    public Container(String json) {
        this(new JSONObject(json));
    }

    public String getCommand() {
        return json.getString("Command");
    }

    public long getCreated() {
        return json.getLong("Created");
    }

    public String getId() {
        return json.getString("Id");
    }

    public String getImage() {
        return json.getString("Image");
    }

    public Map<String, String> getLabels() {
        Map<String, String> map = new HashMap<>();

        JSONObject labels = json.getJSONObject("Labels");
        JSONArray keys = labels.names();

        for (int i = 0; i < keys.length(); i++) {
            String key = keys.getString(i);
            map.put(key, labels.getString(key));
        }

        return map;
    }

    public String getName() {
        for (String name : getNames()) {
            if (name.startsWith("/")) {
                name = name.substring(1);
            }

            if (name.indexOf("/") == -1) {
                return name;
            }
        }

        // this should never happen
        throw new IllegalStateException("unable to determine container name");
    }

    public Collection<String> getNames() {
        JSONArray array = json.getJSONArray("Names");
        ArrayList<String> list = new ArrayList<>(array.length());

        for (int i = 0; i < array.length(); i++) {
            list.add(array.getString(i));
        }

        return list;
    }

    public Collection<Port> getPorts() {
        JSONArray ports = json.getJSONArray("Ports");
        ArrayList<Port> list = new ArrayList<>(ports.length());

        for (int i = 0; i < ports.length(); i++) {
            list.add(new Port(ports.getJSONObject(i)));
        }

        return list;
    }

    public String getStatus() {
        return json.getString("Status");
    }

    public boolean isRunning() {
        return getStatus().contains("Up");
    }

    public class Port {
        private final JSONObject json;

        Port(JSONObject json) {
            this.json = json;
        }

        public String getIp() {
            return json.getString("IP");
        }

        public int getPrivatePort() {
            return json.getInt("PrivatePort");
        }

        public int getPublicPort() {
            return json.getInt("PublicPort");
        }

        public String getType() {
            return json.getString("Type");
        }
    }
}
