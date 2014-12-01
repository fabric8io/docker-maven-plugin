package org.jolokia.docker.maven.access;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Entity holding port mappings which can be set through the configuration.
 *
 * @author roland
 * @since 04.04.14
 */
public class PortMapping {

    // variables (container port -> variable name)
    private final Map<Integer, String> dynamicPortVariables = new HashMap<>();

    // ports map (container port -> host port)
    private final Map<Integer, Integer> containerPortsMap = new HashMap<>();

    // Mapping between vars and dynamics ports which gets filled in lately
    private final Map<String, Integer> portVariables = new HashMap<>();

    // Mapping between ports and the IP they should bind to
    private final Map<Integer, String> bindToMap = new HashMap<>();
    
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
                	String ps[] = port.split(":", 3);
                    if (ps.length == 3) {
                    	mapPorts(ps[0], ps[1], ps[2], variables);
                    } else if (ps.length == 2) {
                    	mapPorts(null, ps[0], ps[1], variables);
                    } else {
                        throw new IllegalArgumentException("Invalid mapping '" + port + "' (must contain at least one :)");
                    }
                } catch (NumberFormatException exp) {
                    throw new IllegalArgumentException("Port mappings must be given in the format <hostPort>:<mappedPort> or " +
                    								 "<bindTo>:<hostPort>:<mappedPort> (e.g. 8080:8080 / 127.0.0.1:8080:8080). " +
                                                     "The given config '" + port + "' doesn't match this",exp);
                }
            }
        }
    }

    /**
     * Whether this mapping contains dynamically assigned ports
     * @return dynamically assigned ports
     */
    public boolean containsDynamicPorts() {
        return dynamicPortVariables.size() > 0;
    }

    /**
     * The name of the variable for a dynamically mapped port
     *
     * @param containerPort the port
     * @return name of the variable or <code>null</code> if there is no such mapping.
     */
    public String getVariableForPort(Integer containerPort) {
        return dynamicPortVariables.get(containerPort);
    }

    /**
     * @return Set of all mapped container ports
     */
    public Set<Integer> getContainerPorts() {
        return containerPortsMap.keySet();
    }

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{\\s*([^\\s]+)\\s*}");

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
    public void updateVariablesWithDynamicPorts(Map<Integer, Integer> dockerObtainedDynamicPorts) {
        for (Map.Entry<Integer,Integer> entry : dockerObtainedDynamicPorts.entrySet()) {
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
    
	private void mapPorts(String bindTo, String hPort,String cPort, Properties variables) {
		Integer containerPort = Integer.parseInt(cPort);
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
		        dynamicPortVariables.put(containerPort, varName);
		    }
		}
		
		if (bindTo != null) {
			// the container port can never be null, so use that as the key
			bindToMap.put(containerPort, bindTo);
		}
		
		containerPortsMap.put(containerPort, hostPort);
	}

    /**
     * Return this object as docker configuration which can be used during startup
     * @return the JSON config holding the port mappings
     */
    public JSONObject toDockerConfig() {
        if (containerPortsMap.size() > 0) {
            JSONObject c = new JSONObject();
            for (Map.Entry<Integer,Integer> entry : containerPortsMap.entrySet()) {
                Integer port = entry.getKey();
                Integer hostPort = entry.getValue();
                JSONObject o = new JSONObject();
                o.put("HostPort",hostPort != null ? hostPort.toString() : "");

                if (bindToMap.containsKey(port)) {
                    o.put("HostIp", bindToMap.get(port));
                }

                JSONArray a = new JSONArray();
                a.put(o);
                c.put(port + "/tcp",a);
            }
            return c;
        } else {
            return null;
        }
    }

    /**
     * Return true if this mapping contains no ports
     */
    public boolean isEmpty() {
        return containerPortsMap.isEmpty();
    }

    Map<Integer, String> getBindToMap() {
    	return bindToMap;
    }

    Map<Integer, Integer> getPortsMap() {
        return containerPortsMap;
    }

}
