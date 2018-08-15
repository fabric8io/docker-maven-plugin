package io.fabric8.maven.docker.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import io.fabric8.maven.docker.util.JsonUtils;


public class ContainerDetails implements Container {

    static final String CONFIG = "Config";
    static final String CREATED = "Created";
    static final String HOST_IP = "HostIp";
    static final String HOST_PORT = "HostPort";
    static final String ID = "Id";
    static final String IMAGE = "Image";
    static final String LABELS = "Labels";
    static final String NAME = "Name";
    static final String IP = "IPAddress";
    static final String NETWORK_SETTINGS = "NetworkSettings";
    static final String NETWORKS = "Networks";
    static final String PORTS = "Ports";
    static final String SLASH = "/";
    static final String STATE = "State";
    static final String HEALTH = "Health";
    static final String STATUS = "Status";
    static final String HEALTH_STATUS_HEALTHY = "healthy";
    static final String HEALTHCHECK = "Healthcheck";
    static final String TEST = "Test";

    private static final String EXIT_CODE = "ExitCode";
    private static final String RUNNING = "Running";

    private final JSONObject json;

    public ContainerDetails(JSONObject json) {
        this.json = json;
    }

    @Override
    public long getCreated() {
        String date = json.optString(CREATED);
        Calendar cal = DatatypeConverter.parseDateTime(date);
        return cal.getTimeInMillis();
    }

    @Override
    public String getId() {
        // only need first 12 to id a container
        return json.optString(ID).substring(0, 12);
    }

    @Override
    public String getImage() {
        // ID: json.optString("Image");
        return json.optJSONObject(CONFIG).optString(IMAGE);
    }

    @Override
    public Map<String, String> getLabels() {
        JSONObject config = json.optJSONObject(CONFIG);
        return config.has(LABELS) ?
                mapLabels(config.optJSONObject(LABELS)) :
                Collections.<String, String>emptyMap();
    }

    @Override
    public String getName() {
        String name = json.optString(NAME);

        if (name.startsWith(SLASH)) {
            name = name.substring(1);
        }
        return name;
    }

    @Override
    public String getIPAddress() {
        if (json.has(NETWORK_SETTINGS) && !json.isNull(NETWORK_SETTINGS)) {
            JSONObject networkSettings = json.optJSONObject(NETWORK_SETTINGS);
            if (!networkSettings.isNull(IP)) {
                return networkSettings.optString(IP);
            }
        }
        return null;
    }

    @Override
    public Map<String, String> getCustomNetworkIpAddresses() {
        if (json.has(NETWORK_SETTINGS) && !json.isNull(NETWORK_SETTINGS)) {
            JSONObject networkSettings = json.optJSONObject(NETWORK_SETTINGS);
            if (networkSettings.has(NETWORKS) && !networkSettings.isNull(NETWORKS)) {
                return extractNetworks(networkSettings);
            }
        }
        return null;
    }

    private Map<String, String> extractNetworks(JSONObject networkSettings) {
        JSONObject networks = networkSettings.optJSONObject(NETWORKS);
        JSONArray keys = networks.names();
        if (keys == null || keys.length() == 0) {
            return null;
        }
        Map<String, String> results = new HashMap<>();
        for (int i = 0; i < keys.length(); i++) {
            String key = keys.optString(i);
            JSONObject net = networks.optJSONObject(key);
            if (net.has(IP) && !net.isNull(IP)) {
                results.put(key, net.optString(IP));
            }
        }

        return results;
    }

    @Override
    public Map<String, PortBinding> getPortBindings() {
        if (json.has(NETWORK_SETTINGS) && !json.isNull(NETWORK_SETTINGS)) {
            JSONObject networkSettings = json.optJSONObject(NETWORK_SETTINGS);
            if (!networkSettings.isNull(PORTS)) {
                return createPortBindings(networkSettings.optJSONObject(PORTS));
            }
        }

        return new HashMap<>();
    }

    @Override
    public boolean isRunning() {
        JSONObject state = json.optJSONObject(STATE);
        return state.optBoolean(RUNNING);
    }

    @Override
    public Integer getExitCode() {
        if (isRunning()) {
            return null;
        }
        JSONObject state = json.optJSONObject(STATE);
        return state.optInt(EXIT_CODE);
    }

    public boolean isHealthy() {
        final JSONObject state = json.optJSONObject(STATE);
        // always indicate healthy for docker hosts that do not support health checks.
        return !state.has(HEALTH) || HEALTH_STATUS_HEALTHY.equals(state.optJSONObject(HEALTH).optString(STATUS));
    }

    public String getHealthcheck() {
        if (!json.optJSONObject(CONFIG).has(HEALTHCHECK) ||
            !json.optJSONObject(CONFIG).optJSONObject(HEALTHCHECK).has(TEST)) {
            return null;
        }
        return JsonUtils.join(json.optJSONObject(CONFIG).optJSONObject(HEALTHCHECK).optJSONArray(TEST),", ");
    }

    private void addPortMapping(String port, JSONObject hostConfig, Map<String, PortBinding> portBindings) {
        String hostIp = hostConfig.optString(HOST_IP);
        Integer hostPort = Integer.valueOf(hostConfig.optString(HOST_PORT));

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

        Iterator<String> iterator = ports.keys();
        while (iterator.hasNext()) {
            String port = iterator.next();
            if (ports.isNull(port)) {
                addPortMapping(port, (PortBinding) null, portBindings);
            } else {
                // use the first entry in the array
                JSONObject hostConfig = ports.optJSONArray(port).optJSONObject(0);
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
            mapped.put(key, labels.opt(key).toString());
        }

        return mapped;
    }
}
