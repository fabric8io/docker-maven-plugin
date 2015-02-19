package org.jolokia.docker.maven.access;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Entity holding port mappings which can be set through the configuration.
 *
 * @author roland
 * @since 04.04.14
 */
public class PortMapping {

    // Pattern for splitting of the protocol
    private static final Pattern PROTOCOL_SPLIT_PATTERN = Pattern.compile("(.*?)(?:/(tcp|udp))?$");

    // Pattern for detecting variables
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{\\s*([^\\s]+)\\s*}");

    // variables (container port spec -> variable name)
    private final Map<String, String> dynamicPortVariables = new HashMap<>();

    // ports map (container port -> host port)
    private final Map<String, Integer> containerPortsMap = new HashMap<>();

    // Mapping between vars and dynamics ports which gets filled in lately
    private final Map<String, Integer> portVariables = new HashMap<>();

    // Mapping between ports and the IP they should bind to
    private final Map<String, String> bindToHostMap = new HashMap<>();
    
    /**
     * Create the mapping from a configuration. The configuation is list of port mapping specifications which has the
     * format used by docker for port mapping (i.e. host_ip:host_port:container_port)
     *
     * <ul>
     *   <li>The "host_ip" part is optional. If not given, the all interfaces are used</li>
     *   <li>If "host_port" is non numeric it is taken as a variable name. If this variable is given as value in variables,
     *       this number is used as host port. If no numeric value is given, it is considered to be filled with the real,
     *       dynamically created port value when {@link #updateVariablesWithDynamicPorts(Map)} is called</li>
     * </ul>
     *
     *
     * @param config a list of configuration strings where each string hast the format <code>host_ip:host_port:container_port</code>. If
     *               the <code>host-port</code> is non-numeric it is assumed to be a variable (which later might be filled in with
     *               the dynamically created port).
     * @param variables variables which should be filled in.
     * @throws IllegalArgumentException if the format doesn't fit
     */
    public PortMapping(List<String> config, Properties variables) {
        if (config != null) {
            for (String port : config) {
                try {
                    Matcher matcher = PROTOCOL_SPLIT_PATTERN.matcher(port);
                    if (!matcher.matches()) {
                        throw new IllegalArgumentException("Invalid mapping '" + port + "'");
                    }
                    String portMapping = matcher.group(1);
                    String protocol = matcher.group(2);
                    if (protocol == null) {
                        protocol = "tcp";
                    }
                    String ps[] = portMapping.split(":", 3);
                    if (ps.length == 3) {
                    	mapPorts(ps[0], ps[1], checkInt(ps[2]) + "/" + protocol, variables);
                    } else if (ps.length == 2) {
                    	mapPorts(null, ps[0], checkInt(ps[1]) + "/" + protocol, variables);
                    } else {
                        throw new IllegalArgumentException("Invalid mapping '" + port + "' (must contain at least one :)");
                    }
                } catch (NumberFormatException exp) {
                    throw new IllegalArgumentException("Port mappings must be given in the format <hostPort>:<mappedPort> or " +
                    								 "<bindTo>:<hostPort>:<mappedPort> (e.g. 8080:8080 / 127.0.0.1:8080:8080). " +
                                                     "A protocol can be appended with '/tcp' or '/udp'. " +
                                                     "The given config '" + port + "' doesn't match this format",exp);
                }
            }
        }
    }

    private String checkInt(String p) throws NumberFormatException {
        // Just check, whether p is an integer or not
        Integer.parseInt(p);
        return p;
    }

    /**
     * Whether this mapping contains dynamically assigned ports
     * @return dynamically assigned ports
     */
    public boolean containsDynamicPorts() {
        return dynamicPortVariables.size() > 0;
    }

    /**
     * @return Set of all mapped container ports
     */
    public Set<String> getContainerPorts() {
        return containerPortsMap.keySet();
    }


    /**
     * Replace all variable expressions with the respective port
     *
     * @param value value to replace
     * @return the modified string
     */
    public String replaceVars(String value) {
        Matcher matcher = VAR_PATTERN.matcher(value);
        StringBuffer ret = new StringBuffer();
        while (matcher.find()) {
            String var = matcher.group(1);
            Integer port = portVariables.get(var);
            matcher.appendReplacement(ret, port != null ? "" + port : "$0");
        }
        matcher.appendTail(ret);
        return ret.toString();
    }

    /**
     * Get all variables which are used for ports
     */
    public Map<String, Integer> getPortVariables() {
        return portVariables;
    }

    /**
     * Update variable-to-port mappings with dynamically obtained ports. This should only be called once after the dynamic
     * ports could be obtained.
     *
     * @param dockerObtainedDynamicPorts keys are the container ports, values are the dynamically mapped host ports,
     */
    public void updateVariablesWithDynamicPorts(Map<String, Integer> dockerObtainedDynamicPorts) {
        for (Map.Entry<String,Integer> entry : dockerObtainedDynamicPorts.entrySet()) {
            String variable = dynamicPortVariables.get(entry.getKey());
            if (variable != null) {
                portVariables.put(variable, entry.getValue());
            }
        }
    }

    // Check for a variable containing a port, return it as integer or <code>null</code> is not found or not a number
    private Integer getPortFromVariable(Properties variables, String var) {
        if (variables.containsKey(var)) {
            try {
                return Integer.parseInt(variables.getProperty(var));
            } catch (NumberFormatException exp) {
                return null;
            }
        } 
        
        return null;
    }
    
	private void mapPorts(String bindToHost, String hPort,String containerPortSpec, Properties variables) {
        Integer hostPort;
		try {
		    hostPort = Integer.parseInt(hPort);
		} catch (NumberFormatException exp) {
            String varName = hPort;
		    // Port should be dynamically assigned and set to the variable give in hPort
		    hostPort = getPortFromVariable(variables, varName);
		    if (hostPort != null) {
                // hPort: Variable name, hostPort: Port coming from the variable
		        portVariables.put(varName, hostPort);
		    } else {
                // containerPort: Port from container, hPort: Variable name to be filled later on
		        dynamicPortVariables.put(containerPortSpec, varName);
		    }
		}
		
		if (bindToHost != null) {
			// the container port can never be null, so use that as the key
		    try {
		        String ipAddress = InetAddress.getByName(bindToHost).getHostAddress();
		        bindToHostMap.put(containerPortSpec, ipAddress);
		    } catch (UnknownHostException e) {
		        throw new IllegalArgumentException("bind host [" + bindToHost + "] cannot be resolved");
		    }
		}
		
		containerPortsMap.put(containerPortSpec, hostPort);
	}

	Map<String, String> getBindToHostMap() {
    	return bindToHostMap;
    }

    Map<String, Integer> getPortsMap() {
        return containerPortsMap;
    }

}
