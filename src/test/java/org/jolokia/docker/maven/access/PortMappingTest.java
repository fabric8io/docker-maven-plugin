package org.jolokia.docker.maven.access;

import java.util.*;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 04.04.14
 */


public class PortMappingTest {

    @Test
    public void variableReplacement() throws MojoExecutionException {

        PortMapping mapping = createPortMapping("jolokia.port:8080","18181:8181","127.0.0.1:9090:9090", "127.0.0.1:other.port:5678");

        updateDynamicMapping(mapping, 8080, 49900);
        updateDynamicMapping(mapping, 5678, 49901);

        mapAndVerifyReplacement(mapping,
                                "http://localhost:49900/", "http://localhost:${jolokia.port}/",
                                "http://pirx:49900/", "http://pirx:${    jolokia.port}/");

        mapAndVerifyReplacement(mapping,
                				"http://localhost:49901/", "http://localhost:${other.port}/",
                				"http://pirx:49901/", "http://pirx:${    other.port}/");

        assertEquals((int) mapping.getPortVariables().get("jolokia.port"), 49900);
        assertEquals((int) mapping.getPortVariables().get("other.port"), 49901);

        assertTrue(mapping.containsDynamicPorts());
        assertEquals(4, mapping.getContainerPorts().size());

        assertEquals("jolokia.port", mapping.getVariableForPort(8080));
        assertEquals("other.port", mapping.getVariableForPort(5678));

        assertEquals(4, mapping.getPortsMap().size());
        assertEquals(2, mapping.getBindToMap().size());

        assertEquals(49900, (long) mapping.getPortVariables().get("jolokia.port"));
        assertEquals(49901, (long) mapping.getPortVariables().get("other.port"));

        Map<Integer,Integer> p = mapping.getPortsMap();
        assertEquals(p.size(),4);

        assertNull(p.get(8080));
        assertNull(p.get(5678));

        assertEquals(18181, (long) p.get(8181));
        assertEquals(9090, (long) p.get(9090));

        assertEquals("127.0.0.1", mapping.getBindToMap().get(9090));
        assertEquals("127.0.0.1", mapping.getBindToMap().get(5678));
    }

    @Test
    public void variableReplacementWitProps() throws MojoExecutionException {
        PortMapping mapping = createPortMapping(p("jolokia.port","50000"),"jolokia.port:8080");
        updateDynamicMapping(mapping, 8080, 49900);
        mapAndVerifyReplacement(mapping,
                                "http://localhost:50000/", "http://localhost:${jolokia.port}/");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidMapping() throws MojoExecutionException {
        createPortMapping("bla");
    }

    @Test(expected = IllegalArgumentException.class)
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
        Map<Integer,Integer> dynMapping = new HashMap<>();
        for (int i = 0; i < ports.length; i+=2) {
            dynMapping.put(ports[i],ports[i+1]);
        }
        mapping.updateVariablesWithDynamicPorts(dynMapping);
    }

    private PortMapping createPortMapping(String ... mappings) throws IllegalArgumentException {
        return createPortMapping(new Properties(),mappings);
    }

    private PortMapping createPortMapping(Properties properties, String ... mappings) throws IllegalArgumentException {
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