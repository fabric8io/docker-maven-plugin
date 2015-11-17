package org.jolokia.docker.maven.model;

import java.util.*;

import javax.xml.bind.DatatypeConverter;

import org.json.JSONObject;


public class ContainerDetails implements Container {

    static final String CONFIG = "Config";
    static final String CREATED = "Created";
    static final String HOST_IP = "HostIp";
    static final String HOST_PORT = "HostPort";
    static final String ID = "Id";
    static final String IMAGE = "Image";
    static final String LABELS = "Labels";
    static final String NAME = "Name";
    static final String NETWORK_SETTINGS = "NetworkSettings";
    static final String PORTS = "Ports";
    static final String SLASH = "/";
    static final String STATE = "State";

    private static final String RUNNING = "Running";
    
    private final JSONObject json;

    public ContainerDetails(JSONObject json) {
        this.json = json;
    }

    @Override
    public long getCreated() {
        String date = json.getString(CREATED);
        Calendar cal = DatatypeConverter.parseDateTime(date);
        return cal.getTimeInMillis();
    }

    @Override
    public String getId() {
        // only need first 12 to id a container
        return json.getString(ID).substring(0, 12);
    }

    @Override
    public String getImage() {
        // ID: json.getString("Image");
        return json.getJSONObject(CONFIG).getString(IMAGE);
    }

    @Override
    public Map<String, String> getLabels() {
        if (!json.getJSONObject(CONFIG).has(LABELS)) {
            return Collections.emptyMap();
        }
         
         return mapLabels(json.getJSONObject(CONFIG).getJSONObject(LABELS));
    }

    @Override
    public String getName() {
        String name = json.getString(NAME);

        if (name.startsWith(SLASH)) {
            name = name.substring(1);
        }
        return name;
    }

    @Override
    public Map<String, PortBinding> getPortBindings() {
        if (json.has(NETWORK_SETTINGS) && !json.isNull(NETWORK_SETTINGS)) {
            JSONObject networkSettings = json.getJSONObject(NETWORK_SETTINGS);
            if (!networkSettings.isNull(PORTS)) {
                return createPortBindings(networkSettings.getJSONObject(PORTS));
            }
        }

        return Collections.emptyMap();
    }

    @Override
    public boolean isRunning() {
        JSONObject state = json.getJSONObject(STATE);
        return state.getBoolean(RUNNING);
    }

    private void addPortMapping(String port, JSONObject hostConfig, Map<String, PortBinding> portBindings) {
        String hostIp = hostConfig.getString(HOST_IP);
        Integer hostPort = Integer.valueOf(hostConfig.getString(HOST_PORT));

        addPortMapping(port, new PortBinding(hostPort, hostIp), portBindings);
    }

    private void addPortMapping(String port, PortBinding binding, Map<String, PortBinding> portBindings) {
        if (port.indexOf('/') == -1) {
            port = port + "/tcp";
        }

        portBindings.put(port, binding);
    }

    private Map<String, PortBinding> createPortBindings(JSONObject ports) {
        Map<String, PortBinding> portBindings = new HashMap<>();

        for (Object obj : ports.keySet()) {
            String port = obj.toString();
            if (ports.isNull(port)) {
                addPortMapping(port, (PortBinding) null, portBindings);
            } else {
                // use the first entry in the array
                JSONObject hostConfig = ports.getJSONArray(port).getJSONObject(0);
                addPortMapping(port, hostConfig, portBindings);
            }
        }

        return portBindings;
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
}
