package io.fabric8.maven.docker.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class ContainersListElement implements Container {

    static final String CREATED = "Created";
    public static final String ID = "Id";
    public static final String IMAGE = "Image";
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

    private final JsonObject json;

    public ContainersListElement(JsonObject json) {
        this.json = json;
    }

    @Override
    public long getCreated() {
        return json.get(CREATED).getAsLong();
    }

    @Override
    public String getId() {
        // only need first 12 to id a container
        return json.get(ID).getAsString().substring(0, 12);
    }

    @Override
    public String getImage() {
        return json.get(IMAGE).getAsString();
    }

    @Override
    public Map<String, String> getLabels() {
       if (!json.has(LABELS) || json.get(LABELS).isJsonNull()) {
           return Collections.emptyMap();
       }

        return mapLabels(json.getAsJsonObject(LABELS));
    }

    @Override
    public String getName() {
        if (json.has(NAMES)) {
            JsonArray names = json.getAsJsonArray(NAMES);
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i).getAsString();
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
    public String getNetworkMode() {
      // HostConfig.NetworkMode is not provided by container list action.
      return null;
    }

    @Override
    public Map<String, PortBinding> getPortBindings() {
        if (json.get(PORTS).isJsonNull()) {
            return Collections.emptyMap();
        }

        return mapPortBindings(json.getAsJsonArray(PORTS));
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
        String status = json.get(STATUS).getAsString();
        return status.toLowerCase().contains(UP);
    }

    @Override
    public Integer getExitCode() {
        // exit code is not provided by container list action.
        return null;
    }

    private PortBinding createPortBinding(JsonObject object) {
        PortBinding binding = null;

        if (object.has(PUBLIC_PORT) && object.has(IP)) {
            binding = new PortBinding(object.get(PUBLIC_PORT).getAsInt(), object.get(IP).getAsString());
        }

        return binding;
    }

    private String createPortKey(JsonObject object) {
        return String.format("%s/%s", object.get(PRIVATE_PORT).getAsInt(), object.get(TYPE).getAsString());
    }

    private Map<String, String> mapLabels(JsonObject labels) {
        int length = labels.size();
        Map<String, String> mapped = new HashMap<>(length);

        Iterator<String> iterator = labels.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            mapped.put(key, labels.get(key).getAsString());
        }

        return mapped;
    }

    private Map<String, PortBinding> mapPortBindings(JsonArray ports) {
        int length = ports.size();
        Map<String, PortBinding> portBindings = new HashMap<>(length);

        for (int i = 0; i < length; i++) {
            JsonObject object = ports.get(i).getAsJsonObject();
            portBindings.put(createPortKey(object), createPortBinding(object));
        }

        return portBindings;
    }
}
