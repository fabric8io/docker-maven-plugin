package org.jolokia.docker.maven.access;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jolokia.docker.maven.model.Container.PortBinding;
import org.jolokia.docker.maven.util.EnvUtil;

/**
 * Entity holding port mappings which can be set through the configuration.
 *
 * @author roland
 * @since 04.04.14
 */
public class PortMapping {

    // Pattern for splitting of the protocol
    private static final Pattern PROTOCOL_SPLIT_PATTERN = Pattern.compile("(.*?)(?:/(tcp|udp))?$");

    // Mapping between ports and the IP they should bind to
    private final Map<String, String> bindToHostMap = new HashMap<>();

    // ports map (container port -> host port)
    private final Map<String, Integer> containerPortToHostPort = new HashMap<>();

    // resolved dynamic properties
    private final Properties dynamicProperties = new Properties();

    // Mapping between property name and host ip (ip filled in after container creation)
    private final Map<String, String> hostIpVariableMap = new HashMap<>();

    // Mapping between property name and host port (port filled in after container creation)
    private final Map<String, Integer> hostPortVariableMap = new HashMap<>();

    // project properties
    private final Properties projProperties;

    // variables (container port spec -> host ip variable name)
    private final Map<String, String> specToHostIpVariableMap = new HashMap<>();

    // variables (container port spec -> host port variable name)
    private final Map<String, String> specToHostPortVariableMap = new HashMap<>();

    /**
     * Create the mapping from a configuration. The configuation is list of port mapping specifications which has the
     * format used by docker for port mapping (i.e. host_ip:host_port:container_port)
     * <ul>
     * <li>The "host_ip" part is optional. If not given, the all interfaces are used</li>
     * <li>If "host_port" is non numeric it is taken as a variable name. If this variable is given as value in
     * variables, this number is used as host port. If no numeric value is given, it is considered to be filled with the
     * real, dynamically created port value when {@link #updateVariablesWithDynamicPorts(Map)} is called</li>
     * </ul>
     *
     * @param portMappings a list of configuration strings where each string hast the format
     *            <code>host_ip:host_port:container_port</code>. If the <code>host-port</code> is non-numeric it is
     *            assumed to be a variable (which later might be filled in with the dynamically created port).
     * @param projProperties project properties
     * @throws IllegalArgumentException if the format doesn't fit
     */
    public PortMapping(List<String> portMappings, Properties projProperties) {
        this.projProperties = projProperties;

        for (String portMapping : portMappings) {
            parsePortMapping(portMapping);
        }
    }

    public boolean containsDynamicHostIps() {
        return !specToHostIpVariableMap.isEmpty();
    }

    /**
     * Whether this mapping contains dynamically assigned ports
     * 
     * @return dynamically assigned ports
     */
    public boolean containsDynamicPorts() {
        return !specToHostPortVariableMap.isEmpty();
    }

    /**
     * @return Set of all mapped container ports
     */
    public Set<String> getContainerPorts() {
        return containerPortToHostPort.keySet();
    }

    public Map<String, Integer> getContainerPortToHostPortMap() {
        return containerPortToHostPort;
    }

    /**
     * Update variable-to-port mappings with dynamically obtained ports. This should only be called once after the
     * dynamic ports could be obtained.
     *
     * @param dockerObtainedDynamicPorts keys are the container ports, values are the dynamically mapped host ports,
     */
    public void updateVariablesWithDynamicPorts(Map<String, PortBinding> dockerObtainedDynamicPorts) {
        for (Map.Entry<String, PortBinding> entry : dockerObtainedDynamicPorts.entrySet()) {
            String variable = entry.getKey();
            PortBinding portBinding = entry.getValue();

            if (portBinding != null) {
                update(hostPortVariableMap, specToHostPortVariableMap.get(variable), portBinding.getHostPort());
                
                String hostIp = portBinding.getHostIp();

                // Use the docker host if binding is on all interfaces
                if ("0.0.0.0".equals(hostIp)) {
                    hostIp = projProperties.getProperty("docker.host.address");
                }
                
                update(hostIpVariableMap, specToHostIpVariableMap.get(variable), hostIp);
            }
        }

        updateDynamicProperties(hostPortVariableMap);
        updateDynamicProperties(hostIpVariableMap);
    }

    // visible for testing
    Map<String, String> getBindToHostMap() {
        return bindToHostMap;
    }
    
    // visible for testing
    Map<String, String> getHostIpVariableMap() {
        return hostIpVariableMap;
    }

    // visible for testing
    Map<String, Integer> getHostPortVariableMap() {
        return hostPortVariableMap;
    }

    // visible for testing
    Map<String, Integer> getPortsMap() {
        return containerPortToHostPort;
    }

    private IllegalArgumentException createInvalidMappingError(String mapping, NumberFormatException exp) {
        return new IllegalArgumentException("\nInvalid port mapping '" + mapping + "'\n" +
                "Required format: '<+bindTo>:<hostPort>:<mappedPort>(/tcp|udp)'\n" +
                "See: https://github.com/rhuss/docker-maven-plugin/blob/master/doc/manual.md#port-mapping");
    }

    private void createMapping(String[] parts, String protocol) {
        if (parts.length == 3) {
            mapBindToAndHostPortSpec(parts[0], parts[1], createPortSpec(parts[2], protocol));
        } else {
            mapHostPortToSpec(parts[0], createPortSpec(parts[1], protocol));
        }
    }

    private String createPortSpec(String port, String protocol) throws NumberFormatException {
        return Integer.parseInt(port) + "/" + protocol;
    }

    private Integer getAsIntOrNull(String val) {
        try {
            return Integer.parseInt(val);
        } catch (@SuppressWarnings("unused") NumberFormatException exp) {
            return null;
        }
    }

    // Check for a variable containing a port, return it as integer or <code>null</code> is not found or not a number
    // First check system properties, then the variables given
    private Integer getPortFromVariableOrSystemProperty(String var) {
        String sysProp = System.getProperty(var);
        if (sysProp != null) {
            return getAsIntOrNull(sysProp);
        }
        if (projProperties.containsKey(var)) {
            return getAsIntOrNull(projProperties.getProperty(var));
        }
        return null;
    }

    private String extractPortPropertyName(String name) {
        String mavenPropName = EnvUtil.extractMavenPropertyName(name);
        return mavenPropName != null ? mavenPropName : name;
    }

    private void mapBindToAndHostPortSpec(String bindTo, String hPort, String portSpec) {
        mapHostPortToSpec(hPort, portSpec);

        String hostPropName = extractHostPropertyName(bindTo);
        if (hostPropName != null) {
            String host = projProperties.getProperty(hostPropName);
            if (host != null) {
                // the container portSpec can never be null, so use that as the key
                bindToHostMap.put(portSpec, resolveHostname(host));
            }

            specToHostIpVariableMap.put(portSpec, hostPropName);
        } else {
            // the container portSpec can never be null, so use that as the key
            bindToHostMap.put(portSpec, resolveHostname(bindTo));
        }
    }

    private String extractHostPropertyName(String name) {
        if (name.startsWith("+")) {
            return name.substring(1);
        } else {
            return EnvUtil.extractMavenPropertyName(name);
        }
    }

    private void mapHostPortToSpec(String hPort, String portSpec) {
        Integer hostPort;
        try {
            hostPort = Integer.parseInt(hPort);
        } catch (@SuppressWarnings("unused") NumberFormatException exp) {
            // Port should be dynamically assigned and set to the variable give in hPort
            String portPropertyName = extractPortPropertyName(hPort);

            hostPort = getPortFromVariableOrSystemProperty(portPropertyName);
            if (hostPort != null) {
                // hPort: Variable name, hostPort: Port coming from the variable
                hostPortVariableMap.put(portPropertyName, hostPort);
            } else {
                // containerPort: Port from container, hPort: Variable name to be filled later on
                specToHostPortVariableMap.put(portSpec, portPropertyName);
            }
        }
        containerPortToHostPort.put(portSpec, hostPort);
    }

    private void parsePortMapping(String input) throws IllegalArgumentException {
        try {
            Matcher matcher = PROTOCOL_SPLIT_PATTERN.matcher(input);
            if (input.indexOf(':') == -1 || !matcher.matches()) {
                throw createInvalidMappingError(input, null);
            }

            String mapping = matcher.group(1);
            String protocol = matcher.group(2);
            if (protocol == null) {
                protocol = "tcp";
            }

            createMapping(mapping.split(":", 3), protocol);
        } catch (NumberFormatException exp) {
            throw createInvalidMappingError(input, exp);
        }
    }

    private String resolveHostname(String bindToHost) {
        try {
            return InetAddress.getByName(bindToHost).getHostAddress();
        } catch (@SuppressWarnings("unused") UnknownHostException e) {
            throw new IllegalArgumentException("Host '" + bindToHost + "' to bind to cannot be resolved");
        }
    }

    private <T> void update(Map<String, T> map, String key, T value) {
        if (key != null) {
            map.put(key, value);
        }
    }

    private void updateDynamicProperties(Map<String, ?> dynamicPorts) {
        for (Map.Entry<String, ?> entry : dynamicPorts.entrySet()) {
            String var = entry.getKey();
            String val = entry.getValue().toString();

            projProperties.setProperty(var, val);
            dynamicProperties.setProperty(var, val);
        }
    }

    public static class PropertyWriteHelper {

        private final Properties globalExport;

        private final String globalFile;
        private final Map<String, Properties> toExport;

        public PropertyWriteHelper(String globalFile) {
            this.globalFile = globalFile;

            this.toExport = new HashMap<>();
            this.globalExport = new Properties();
        }

        public void add(PortMapping portMapping, String portPropertyFile) {
            if (portPropertyFile != null) {
                toExport.put(portPropertyFile, portMapping.dynamicProperties);
            } else if (globalFile != null) {
                globalExport.putAll(portMapping.dynamicProperties);
            }
        }

        public void write() throws IOException {
            for (Map.Entry<String, Properties> entry : toExport.entrySet()) {
                Properties props = entry.getValue();
                writeProperties(props, entry.getKey());
                globalExport.putAll(props);
            }

            if (globalFile != null && !globalExport.isEmpty()) {
                writeProperties(globalExport, globalFile);
            }
        }

        private void writeProperties(Properties props, String file) throws IOException {
            File propFile = new File(file);
            try (OutputStream os = new FileOutputStream(propFile)) {
                props.store(os, "Docker ports");
            } catch (IOException e) {
                throw new IOException("Cannot write properties to " + file + ": " + e, e);
            }
        }
    }
}
