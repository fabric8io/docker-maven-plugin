package org.jolokia.docker.maven.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * Utility class for encapsulating port mappings as given in the configuration.
 *
 * @author roland
 * @since 04.04.14
 */
public class PortMapping {

    // variables (container port -> variable name)
    private final Map<Integer, String> varMap = new HashMap<>();

    // ports map (container port -> host port)
    private final Map<Integer, Integer> portsMap = new HashMap<>();

    // Mapping between vars and dynamics ports which gets filled in lately
    private final Map<String, Integer> dynamicPorts = new HashMap<>();

    private final Map<Integer, String> bindToMap = new HashMap<>();
    
    /**
     * Create the mapping
     * @param config a list of configuration strings where each string hast the format <code>host-port:container-port</code>. If
     *               the <code>host-port</code> is non-numeric it is assumed to be a maven variable (which later might be filled in with
     *               the dynamically created port)
     * @param variables project variables which could contain properties
     * @throws MojoExecutionException if the format doesn't fit
     */
    public PortMapping(List<String> config, Properties variables) throws MojoExecutionException {
        if (config != null) {
            for (String port : config) {
                try {
                	String ps[] = port.split(":", 3);
                    if (ps.length == 3) {
                    	mapPorts(ps[0], ps[1], ps[2], variables);
                    } else if (ps.length == 2) {
                    	mapPorts(null, ps[0], ps[1], variables);
                    } else {
                        throw new MojoExecutionException("Invalid mapping '" + port + "' (must contain at least one :)");
                    }
                } catch (NumberFormatException exp) {
                    throw new MojoExecutionException("Port mappings must be given in the format <hostPort>:<mappedPort> or " +
                    								 "<bindTo>:<hostPort>:<mappedPort> (e.g. 8080:8080 / 127.0.0.1:8080:8080). " +
                                                     "The given config '" + port + "' doesn't match this",exp);
                }
            }
        }
    }

    /**
     * Get the local address a container should bind to
     * 
     * @return map of container ports and the local ip addresses they should be bound to
     */
    public Map<Integer, String> getBindToMap() {
    	return bindToMap;
    }
    
    /**
     * Get the port mapping as map
     *
     * @return map with keys being container ports and values the mapped host ports (which are <code>null</code> in case
     *         of dynamically assigned ports
     */
    public Map<Integer, Integer> getPortsMap() {
        return portsMap;
    }

    /**
     * Whether this mapping contains dynamically assigned ports
     * @return dynamically assigned ports
     */
    public boolean containsDynamicPorts() {
        return varMap.size() > 0;
    }

    /**
     * The name of the variable for a dynamically mapped port
     *
     * @param containerPort the port
     * @return name of the variable or <code>null</code> if there is no such mapping.
     */
    public String getVariableForPort(Integer containerPort) {
        return varMap.get(containerPort);
    }

    /**
     * @return Set of all mapped container ports
     */
    public Set<Integer> getContainerPorts() {
        return portsMap.keySet();
    }

    /**
     * Get the port for a variable, or null if the variable is not mapped.
     *
     * @param var variable name
     * @return port associated with this variable or null.
     */
    public Integer getPortForVariable(String var) {
        return dynamicPorts.get(var);
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
            Integer port = getPortForVariable(var);
            matcher.appendReplacement(ret, port != null ? "" + port : "$0");
        }
        matcher.appendTail(ret);
        return ret.toString();
    }

    /**
     * Update the dynamic port mapping with the set given here. Should be called only once when dynamically
     * mapped ports are obtained.
     *
     * @param dockerObtainedDynamicPorts keys are the container ports, values are the dynamically mapped host ports,
     */
    public void updateVarsForDynamicPorts(Map<Integer, Integer> dockerObtainedDynamicPorts) {
        for (Map.Entry<Integer,Integer> entry : dockerObtainedDynamicPorts.entrySet()) {
            String var = varMap.get(entry.getKey());
            if (var != null) {
                dynamicPorts.put(var,entry.getValue());
            }
        }
    }

    public Map<String, Integer> getDynamicPorts() {
        return dynamicPorts;
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
		    // Port should be dynamically assigned and set to the variable give in hPort
		    hostPort = getPortFromVariable(variables, hPort);
		    if (hostPort != null) {
		        dynamicPorts.put(hPort, hostPort);
		    } else {
		        varMap.put(containerPort, hPort);
		    }
		}
		
		if (bindTo != null) {
			// the container port can never be null, so use that as the key
			bindToMap.put(containerPort, bindTo);
		}
		
		portsMap.put(containerPort, hostPort);
	}
}
