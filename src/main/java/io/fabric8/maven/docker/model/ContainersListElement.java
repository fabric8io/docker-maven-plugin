package io.fabric8.maven.docker.model;

import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;

public class ContainersListElement implements Container {

    static final String CREATED = "Created";
    static final String ID = "Id";
    static final String IMAGE = "Image";
    static final String IP = "IP";
    static final String LABELS = "Labels";
    static final String PORTS = "Ports";
    static final String PUBLIC_PORT = "PublicPort";
    static final String STATUS = "Status";
    static final String TYPE = "Type";

    private static final String NAMES = "Names";
    private static final String PRIVATE_PORT = "PrivatePort";
    private static final String SLASH = "/";
    private static final String UP = "up";

    private final JSONObject json;

    public ContainersListElement(JSONObject json) {
        this.json = json;
    }

    @Override
    public long getCreated() {
        return json.getLong(CREATED);
    }

    @Override
    public String getId() {
        // only need first 12 to id a container
        return json.getString(ID).substring(0, 12);
    }

    @Override
    public String getImage() {
        return json.getString(IMAGE);
    }

    @Override
    public Map<String, String> getLabels() {
       if (json.isNull(LABELS)) {
           return Collections.emptyMap();
       }

        return mapLabels(json.getJSONObject(LABELS));
    }

    @Override
    public String getName() {
        if (json.has(NAMES)) {
            JSONArray names = json.getJSONArray(NAMES);
            for (int i = 0; i < names.length(); i++) {
                String name = names.getString(i);
                if (name.startsWith(SLASH)) {
                    name = name.substring(1);
                }
                if (!name.contains(SLASH)) {
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
    public Map<String, PortBinding> getPortBindings() {
        if (json.isNull(PORTS)) {
            return Collections.emptyMap();
        }

        return mapPortBindings(json.getJSONArray(PORTS));
    }

    @Override
    public String getIPAddress() {
        // IP address is not provided by container list action.
        return null;
    }

    @Override
    public Map<String, String> getCustomNetworkIpAddresses() {
        // IP address is not provided by container list action.
        return null;
    }

    @Override
    public boolean isRunning() {
        String status = json.getString(STATUS);
        return status.toLowerCase().contains(UP);
    }

    private PortBinding createPortBinding(JSONObject object) {
        PortBinding binding = null;

        if (object.has(PUBLIC_PORT) && object.has(IP)) {
            binding = new PortBinding(object.getInt(PUBLIC_PORT), object.getString(IP));
        }

        return binding;
    }

    private String createPortKey(JSONObject object) {
        return String.format("%s/%s", object.getInt(PRIVATE_PORT), object.getString(TYPE));
    }

    private Map<String, String> mapLabels(JSONObject labels) {
        int length = labels.length();
        Map<String, String> mapped = new HashMap<>(length);

        Iterator<String> iterator = labels.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            mapped.put(key, labels.get(key).toString());
        }

        return mapped;
    }

    private Map<String, PortBinding> mapPortBindings(JSONArray ports) {
        int length = ports.length();
        Map<String, PortBinding> portBindings = new HashMap<>(length);

        for (int i = 0; i < length; i++) {
            JSONObject object = ports.getJSONObject(i);
            portBindings.put(createPortKey(object), createPortBinding(object));
        }

        return portBindings;
    }
}
