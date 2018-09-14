package io.fabric8.maven.docker.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static io.fabric8.maven.docker.util.JsonUtils.get;

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
        return json.optLong(CREATED);
    }

    @Override
    public String getId() {
        // only need first 12 to id a container
        return json.optString(ID).substring(0, 12);
    }

    @Override
    public String getImage() {
        return json.optString(IMAGE);
    }

    @Override
    public Map<String, String> getLabels() {
       if (json.isNull(LABELS)) {
           return Collections.emptyMap();
       }

        return mapLabels(json.optJSONObject(LABELS));
    }

    @Override
    public String getName() {
        if (json.has(NAMES)) {
            JSONArray names = json.optJSONArray(NAMES);
            for (int i = 0; i < names.length(); i++) {
                String name = names.optString(i);
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

        return mapPortBindings(json.optJSONArray(PORTS));
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
        String status = json.optString(STATUS);
        return status.toLowerCase().contains(UP);
    }

    @Override
    public Integer getExitCode() {
        // exit code is not provided by container list action.
        return null;
    }

    private PortBinding createPortBinding(JSONObject object) {
        PortBinding binding = null;

        if (object.has(PUBLIC_PORT) && object.has(IP)) {
            binding = new PortBinding(object.optInt(PUBLIC_PORT), object.optString(IP));
        }

        return binding;
    }

    private String createPortKey(JSONObject object) {
        return String.format("%s/%s", object.optInt(PRIVATE_PORT), object.optString(TYPE));
    }

    private Map<String, String> mapLabels(JSONObject labels) {
        int length = labels.length();
        Map<String, String> mapped = new HashMap<>(length);

        Iterator<String> iterator = labels.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            mapped.put(key, get(labels, key).toString());
        }

        return mapped;
    }

    private Map<String, PortBinding> mapPortBindings(JSONArray ports) {
        int length = ports.length();
        Map<String, PortBinding> portBindings = new HashMap<>(length);

        for (int i = 0; i < length; i++) {
            JSONObject object = ports.optJSONObject(i);
            portBindings.put(createPortKey(object), createPortBinding(object));
        }

        return portBindings;
    }
}
