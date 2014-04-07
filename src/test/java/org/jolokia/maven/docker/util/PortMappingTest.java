package org.jolokia.maven.docker.util;

import java.util.*;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author roland
 * @since 04.04.14
 */


public class PortMappingTest {

    @Test
    public void variableReplacement() throws MojoExecutionException {
        PortMapping mapping = new PortMapping(Arrays.asList("jolokia.port:8080"), project.getProperties());
        Map<Integer,Integer> dynMapping = new HashMap<Integer, Integer>();
        dynMapping.put(8080,49900);
        mapping.updateVarsForDynamicPorts(dynMapping);
        assertEquals("http://localhost:49900/", mapping.replaceVars("http://localhost:${jolokia.port}/"));
    }
}
