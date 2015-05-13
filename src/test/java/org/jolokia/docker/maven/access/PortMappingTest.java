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

        assertEquals(4, mapping.getPortsMap().size());
        assertEquals(2, mapping.getBindToHostMap().size());

        assertEquals(49900, (long) mapping.getPortVariables().get("jolokia.port"));
        assertEquals(49901, (long) mapping.getPortVariables().get("other.port"));

        Map<String,Integer> p = mapping.getPortsMap();
        assertEquals(p.size(),4);

        assertNull(p.get("8080/tcp"));
        assertNull(p.get("5678/tcp"));

        assertEquals(18181, (long) p.get("8181/tcp"));
        assertEquals(9090, (long) p.get("9090/tcp"));

        assertEquals("127.0.0.1", mapping.getBindToHostMap().get("9090/tcp"));
        assertEquals("127.0.0.1", mapping.getBindToHostMap().get("5678/tcp"));
    }

    @Test
    public void udpAsProtocol() {
        PortMapping mapping = createPortMapping("49000:8080/udp","127.0.0.1:49001:8081/udp");
        Map<String,Integer> p = mapping.getPortsMap();
        assertEquals(2,p.size());
        assertEquals(49000, (long) p.get("8080/udp"));
        assertEquals(49001, (long) p.get("8081/udp"));

        assertEquals("127.0.0.1",mapping.getBindToHostMap().get("8081/udp"));
        assertNull(mapping.getBindToHostMap().get("8080/udp"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidProtocol() {
        createPortMapping("49000:8080/abc");
    }

    @Test
    public void variableReplacementWithProps() throws MojoExecutionException {
        PortMapping mapping = createPortMapping(p("jolokia.port","50000"),"jolokia.port:8080");
        updateDynamicMapping(mapping, 8080, 49900);
        mapAndVerifyReplacement(mapping,
                                "http://localhost:50000/", "http://localhost:${jolokia.port}/");
    }

    @Test
    public void variableReplacementWithSystemPropertyOverwrite() throws MojoExecutionException {
        try {
            System.setProperty("jolokia.port","99999");
            PortMapping mapping = createPortMapping(p("jolokia.port","50000"),"jolokia.port:8080");
            mapAndVerifyReplacement(mapping,
                                    "http://localhost:99999/", "http://localhost:${jolokia.port}/");
        } finally {
            System.getProperties().remove("jolokia.port");
        }
    }

    @Test
    public void testValidHostname() {
        for (String host : new String[] { "localhost", "127.0.0.1" }){
            PortMapping mapping = createPortMapping(host + ":80:80");
            assertTrue(mapping.getBindToHostMap().values().contains("127.0.0.1"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidHostname() {
        createPortMapping("does-not-exist.pvt:80:80");
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
            assertEquals(args[i],mapping.replaceVars(args[i + 1]));
        }
    }

    private void updateDynamicMapping(PortMapping mapping, int ... ports) {
        Map<String,Integer> dynMapping = new HashMap<>();
        for (int i = 0; i < ports.length; i+=2) {
            dynMapping.put(ports[i] + "/tcp",ports[i+1]);
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