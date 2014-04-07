package org.jolokia.maven.docker.util;

import java.util.*;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author roland
 * @since 04.04.14
 */


public class PortMappingTest {

    @Test
    public void variableReplacement() throws MojoExecutionException {

        PortMapping mapping = createPortMapping("jolokia.port:8080","18181:8181");
        updateDynamicMapping(mapping, 8080, 49900);
        mapAndVerifyReplacement(mapping,
                                "http://localhost:49900/", "http://localhost:${jolokia.port}/",
                                "http://pirx:49900/", "http://pirx:${    jolokia.port}/");
        assertEquals((int) mapping.getPortForVariable("jolokia.port"), 49900);
        assertTrue(mapping.containsDynamicPorts());
        assertEquals(mapping.getContainerPorts().size(),2);
        assertEquals(mapping.getVariableForPort(8080),"jolokia.port");
        assertEquals(mapping.getDynamicPorts().size(),1);
        assertEquals((long) mapping.getDynamicPorts().get("jolokia.port"),49900);
        Map<Integer,Integer> p = mapping.getPortsMap();
        assertEquals(p.size(),2);
        assertEquals(p.get(8080),null);
        assertEquals((long) p.get(8181),18181);
    }

    @Test
    public void variableReplacementWitProps() throws MojoExecutionException {
        PortMapping mapping = createPortMapping(p("jolokia.port","50000"),"jolokia.port:8080");
        updateDynamicMapping(mapping, 8080, 49900);
        mapAndVerifyReplacement(mapping,
                                "http://localhost:50000/", "http://localhost:${jolokia.port}/");
    }

    @Test(expected = MojoExecutionException.class)
    public void invalidMapping() throws MojoExecutionException {
        createPortMapping("bla");
    }

    @Test(expected = MojoExecutionException.class)
    public void invalidMapping2() throws MojoExecutionException {
        createPortMapping("jolokia.port:bla");
    }
    // =======================================================================================================

    private void mapAndVerifyReplacement(PortMapping mapping, String... args) {
        for (int i = 0; i < args.length; i+=2) {
            assertEquals(args[i],mapping.replaceVars(args[i+1]));
        }
    }

    private void updateDynamicMapping(PortMapping mapping, int ... ports) {
        Map<Integer,Integer> dynMapping = new HashMap<Integer, Integer>();
        for (int i = 0; i < ports.length; i+=2) {
            dynMapping.put(ports[i],ports[i+1]);
        }
        mapping.updateVarsForDynamicPorts(dynMapping);
    }

    private PortMapping createPortMapping(String ... mappings) throws MojoExecutionException {
        return createPortMapping(new Properties(),mappings);
    }

    private PortMapping createPortMapping(Properties properties, String ... mappings) throws MojoExecutionException {
        return new PortMapping(Arrays.asList(mappings),properties);
    }

    private Properties p(String ... p) {
        Properties ret = new Properties();
        for (int i = 0; i < p.length; i += 2) {
            ret.setProperty(p[i],p[i+1]);
        }
        return ret;
    }
}
