package io.fabric8.maven.docker.model;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.gson.JsonObject;


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
    static final String HOST_CONFIG = "HostConfig";
    static final String NETWORK_MODE = "NetworkMode";
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

    private final JsonObject json;

    public ContainerDetails(JsonObject json) {
        this.json = json;
    }

    @Override
    public long getCreated() {
        String date = json.get(CREATED).getAsString();
        Instant instant = Instant.parse(date);
        return instant.toEpochMilli();
    }

    @Override
    public String getId() {
        // only need first 12 to id a container
        return json.get(ID).getAsString().substring(0, 12);
    }

    @Override
    public String getImage() {
        // ID: json.getString("Image");
        return json.getAsJsonObject(CONFIG).get(IMAGE).getAsString();
    }

    @Override
    public Map<String, String> getLabels() {
        JsonObject config = json.getAsJsonObject(CONFIG);
        return config.has(LABELS) ?
                mapLabels(config.getAsJsonObject(LABELS)) :
                Collections.<String, String>emptyMap();
    }

    @Override
    public String getName() {
        String name = json.get(NAME).getAsString();

        if (name.startsWith(SLASH)) {
            name = name.substring(1);
        }
        return name;
    }

    @Override
    public String getIPAddress() {
        if (json.has(NETWORK_SETTINGS) && !json.get(NETWORK_SETTINGS).isJsonNull()) {
            JsonObject networkSettings = json.getAsJsonObject(NETWORK_SETTINGS);
            if (!networkSettings.get(IP).isJsonNull()) {
                return networkSettings.get(IP).getAsString();
            }
        }
        return null;
    }

		@Override
		public String getNetworkMode() {
			if (json.has(HOST_CONFIG) && !json.get(HOST_CONFIG).isJsonNull()) {
				final JsonObject hostConfig = json.getAsJsonObject(HOST_CONFIG);
				if (!hostConfig.get(NETWORK_MODE).isJsonNull()) {
          return hostConfig.get(NETWORK_MODE).getAsString();
				}
			}
			return null;
		}

    @Override
    public Map<String, String> getCustomNetworkIpAddresses() {
        if (json.has(NETWORK_SETTINGS) && !json.get(NETWORK_SETTINGS).isJsonNull()) {
            JsonObject networkSettings = json.getAsJsonObject(NETWORK_SETTINGS);
            if (networkSettings.has(NETWORKS) && !networkSettings.get(NETWORKS).isJsonNull()) {
                return extractNetworks(networkSettings);
            }
        }
        return null;
    }

    private Map<String, String> extractNetworks(JsonObject networkSettings) {
        JsonObject networks = networkSettings.getAsJsonObject(NETWORKS);
        Set<String> keys = networks.keySet();
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        Map<String, String> results = new HashMap<>();
        for (String key : keys) {
            JsonObject net = networks.getAsJsonObject(key);
            if (net.has(IP) && !net.get(IP).isJsonNull()) {
                results.put(key, net.get(IP).getAsString());
            }
        }

        return results;
    }

    @Override
    public Map<String, PortBinding> getPortBindings() {
        if (json.has(NETWORK_SETTINGS) && !json.get(NETWORK_SETTINGS).isJsonNull()) {
            JsonObject networkSettings = json.getAsJsonObject(NETWORK_SETTINGS);
            if (networkSettings.has(PORTS) && !networkSettings.get(PORTS).isJsonNull()) {
                return createPortBindings(networkSettings.getAsJsonObject(PORTS));
            }
        }

        return new HashMap<>();
    }

    @Override
    public boolean isRunning() {
        JsonObject state = json.getAsJsonObject(STATE);
        return state.get(RUNNING).getAsBoolean();
    }

    @Override
    public Integer getExitCode() {
        if (isRunning()) {
            return null;
        }
        JsonObject state = json.getAsJsonObject(STATE);
        return state.get(EXIT_CODE).getAsInt();
    }

    public boolean isHealthy() {
        final JsonObject state = json.getAsJsonObject(STATE);
        // always indicate healthy for docker hosts that do not support health checks.
        return !state.has(HEALTH) || HEALTH_STATUS_HEALTHY.equals(state.getAsJsonObject(HEALTH).get(STATUS).getAsString());
    }

    public String getHealthcheck() {
        if (!json.getAsJsonObject(CONFIG).has(HEALTHCHECK) ||
            !json.getAsJsonObject(CONFIG).getAsJsonObject(HEALTHCHECK).has(TEST)) {
            return null;
        }

        return Joiner.on(", ").join(json.getAsJsonObject(CONFIG).getAsJsonObject(HEALTHCHECK).getAsJsonArray(TEST));
    }

    private void addPortMapping(String port, JsonObject hostConfig, Map<String, PortBinding> portBindings) {
        String hostIp = hostConfig.get(HOST_IP).getAsString();
        Integer hostPort = Integer.valueOf(hostConfig.get(HOST_PORT).getAsInt());

        addPortMapping(port, new PortBinding(hostPort, hostIp), portBindings);
    }

    private void addPortMapping(String port, PortBinding binding, Map<String, PortBinding> portBindings) {
        if (port.indexOf('/') == -1) {
            port = port + "/tcp";
        }

        portBindings.put(port, binding);
    }

    private Map<String, PortBinding> createPortBindings(JsonObject ports) {
        Map<String, PortBinding> portBindings = new HashMap<>();

        for (Object obj : ports.keySet()) {
            String port = obj.toString();
            if (ports.get(port).isJsonNull()) {
                addPortMapping(port, (PortBinding) null, portBindings);
            } else {
                // use the first entry in the array
                JsonObject hostConfig = ports.getAsJsonArray(port).get(0).getAsJsonObject();
                addPortMapping(port, hostConfig, portBindings);
            }
        }

        return portBindings;
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
}
