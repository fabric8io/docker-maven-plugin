package io.fabric8.maven.docker.access;

import java.util.*;

import io.fabric8.maven.docker.model.Container;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.json.JSONArray;
import org.junit.*;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONParser;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 04.04.14
 */
public class PortMappingTest {

    private PortMapping mapping;

    private Properties properties;

    @Before
    public void setup() {
        properties = new Properties();
    }

    @Test
    public void testComplexMapping() {
        
        givenAHostIpProperty("other.ip", "127.0.0.1");
        
        givenAPortMapping("jolokia.port:8080", "18181:8181", "127.0.0.1:9090:9090", "+other.ip:${other.port}:5678");
        whenUpdateDynamicMapping(443);
        
        whenUpdateDynamicMapping(8080, 49900, "0.0.0.0");
        whenUpdateDynamicMapping(5678, 49901, "127.0.0.1");

        thenMapAndVerifyReplacement("http://localhost:49900/", "http://localhost:${jolokia.port}/",
                "http://pirx:49900/", "http://pirx:${jolokia.port}/");
        thenMapAndVerifyReplacement("http://localhost:49901/", "http://localhost:${other.port}/",
                "http://pirx:49901/", "http://pirx:${other.port}/",
                "http://49900/49901", "http://${jolokia.port}/${other.port}");

        thenNeedsPropertyUpdate();

        thenDynamicHostPortsSizeIs(2);
        thenHostPortVariableEquals("jolokia.port", 49900);
        thenHostPortVariableEquals("other.port", 49901);

        thenDynamicHostIpsSizeIs(1);
        thenHostIpVariableEquals("other.ip", "127.0.0.1");

        thenContainerPortToHostPortMapSizeIs(4);
        thenContainerPortToHostPortMapHasOnlyPortSpec("8080/tcp");
        thenContainerPortToHostPortMapHasOnlyPortSpec("5678/tcp");
        thenContainerPortToHostPortMapHasPortSpecAndPort("8181/tcp", 18181);
        thenContainerPortToHostPortMapHasPortSpecAndPort("9090/tcp", 9090);

        thenBindToHostMapSizeIs(2);
        thenBindToHostMapContains("9090/tcp", "127.0.0.1");
        thenBindToHostMapContains("5678/tcp", "127.0.0.1");
    }
    

    @Test
    public void testHostIpAsPropertyOnly() {
        givenADockerHostAddress("1.2.3.4");
        givenAPortMapping("${other.ip}:5677:5677");
        whenUpdateDynamicMapping(5677, 5677, "0.0.0.0");

        thenContainerPortToHostPortMapSizeIs(1);

        thenDynamicHostPortsSizeIs(0);
        thenDynamicHostIpsSizeIs(1);
        thenBindToHostMapSizeIs(0);
        
        thenHostIpVariableEquals("other.ip", "1.2.3.4");
    }
    
    @Test
    public void testHostIpPortAsProperties() {
        givenADockerHostAddress("5.6.7.8");
        givenAPortMapping("+other.ip:other.port:5677");
        whenUpdateDynamicMapping(5677, 49900, "1.2.3.4");

        thenContainerPortToHostPortMapHasOnlyPortSpec("5677/tcp");

        thenDynamicHostPortsSizeIs(1);
        thenDynamicHostIpsSizeIs(1);

        thenHostPortVariableEquals("other.port", 49900);
        thenHostIpVariableEquals("other.ip", "1.2.3.4");
    }
    
    @Test
    public void testHostIpVariableReplacement() {
        givenAPortMapping("jolokia.port:8080");
        whenUpdateDynamicMapping(8080, 49900, "0.0.0.0");

        thenNeedsPropertyUpdate();
        thenDynamicHostPortsSizeIs(1);
        thenHostPortVariableEquals("jolokia.port", 49900);
    }

    @Test
    public void testHostnameAsBindHost() {
        givenAPortMapping("localhost:80:80");
        thenBindToHostMapContainsValue("127.0.0.1");
    }

    @Test
    public void testSingleContainerPort() {
        givenAPortMapping("8080");
        thenContainerPortToHostPortMapSizeIs(1);
        thenContainerPortToHostPortMapHasOnlyPortSpec("8080");
    }

    @Test
    public void testHostPortAsPropertyOnly() {
        givenAPortMapping("other.port:5677");
        whenUpdateDynamicMapping(5677, 49900, "0.0.0.0");

        thenContainerPortToHostPortMapSizeIs(1);

        thenDynamicHostPortsSizeIs(1);
        thenDynamicHostIpsSizeIs(0);

        thenHostPortVariableEquals("other.port", 49900);
    }

    @Ignore
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidHostnameWithDynamicPort() {
        givenAPortMapping("does-not-exist.pvt:web.port:80");
    }
    
    @Ignore
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidHostnameWithFixedPort() {
        givenAPortMapping("does-not-exist.pvt:80:80");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidMapping() {
        givenAPortMapping("bla");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidMapping2() {
        givenAPortMapping("jolokia.port:bla");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidProtocol() {
        givenAPortMapping("49000:8080/abc");
    }

    @Test
    public void testIpAsBindHost() {
        givenAPortMapping("127.0.0.1:80:80");
        thenBindToHostMapContainsValue("127.0.0.1");
    }

    @Test
    public void testUdpAsProtocol() {
        givenAPortMapping("49000:8080/udp", "127.0.0.1:49001:8081/udp");
        thenContainerPortToHostPortMapSizeIs(2);
        thenContainerPortToHostPortMapHasPortSpecAndPort("8080/udp", 49000);
        thenContainerPortToHostPortMapHasPortSpecAndPort("8081/udp", 49001);
        thenBindToHostMapContains("8081/udp", "127.0.0.1");
        thenBindToHostMapContainsOnlySpec("8080/udp");
    }

    @Test
    public void testVariableReplacementWithProps() {
        givenExistingProperty("jolokia.port", "50000");
        givenAPortMapping("jolokia.port:8080");
        whenUpdateDynamicMapping(8080, 49900, "0.0.0.0");
        thenMapAndVerifyReplacement("http://localhost:50000/", "http://localhost:${jolokia.port}/");
    }

    @Test
    public void testVariableReplacementWithSystemPropertyOverwrite() {
        try {
            System.setProperty("jolokia.port", "99999");
            givenExistingProperty("jolokia.port", "50000");
            givenAPortMapping("jolokia.port:8080");
            thenMapAndVerifyReplacement("http://localhost:99999/", "http://localhost:${jolokia.port}/");
        } finally {
            System.getProperties().remove("jolokia.port");
        }
    }

    @Test
    public void testToJson() {
        givenAPortMapping("49000:8080/udp", "127.0.0.1:49001:8081");
        thenAssertJsonEquals("[{ hostPort: 49000, containerPort: 8080, protocol: udp }," +
                             " { hostIP: '127.0.0.1', hostPort: 49001, containerPort: 8081, protocol: tcp}]");
    }

    private void thenAssertJsonEquals(String json) {
        JSONArray jsonArray = (JSONArray) JSONParser.parseJSON(json);
        JSONAssert.assertEquals(jsonArray,mapping.toJson(), true);
    }

    private void givenADockerHostAddress(String host) {
        properties.setProperty("docker.host.address", host);
    }
    
    private void givenAHostIpProperty(String property, String hostIp) {
        properties.put(property, hostIp);
    }
    
    private void givenAPortMapping(String... mappings) {
        mapping = new PortMapping(Arrays.asList(mappings), properties);
    }

    private void givenExistingProperty(String... p) {
        for (int i = 0; i < p.length; i += 2) {
            properties.setProperty(p[i], p[i + 1]);
        }
    }

    private void thenBindToHostMapContains(String portSpec, String hostIp) {
        assertEquals(hostIp, mapping.getBindToHostMap().get(portSpec));
    }

    private void thenBindToHostMapContainsOnlySpec(String portSpec) {
        assertNull(mapping.getBindToHostMap().get(portSpec));
    }

    private void thenBindToHostMapContainsValue(String host) {
        assertTrue(mapping.getBindToHostMap().values().contains(host));
    }

    private void thenBindToHostMapSizeIs(int size) {
        assertEquals(size, mapping.getBindToHostMap().size());
    }

    private void thenContainerPortToHostPortMapHasOnlyPortSpec(String portSpec) {
        assertNull(mapping.getContainerPortToHostPortMap().get(portSpec));
    }

    private void thenContainerPortToHostPortMapHasPortSpecAndPort(String portSpec, Integer port) {
        assertTrue(mapping.getContainerPorts().contains(portSpec));
        assertEquals(port, mapping.getPortsMap().get(portSpec));
    }

    private void thenContainerPortToHostPortMapSizeIs(int size) {
        assertEquals(size, mapping.getContainerPortToHostPortMap().size());
    }

    private void thenDynamicHostIpsSizeIs(int size) {
        assertEquals(size, mapping.getHostIpVariableMap().size());
    }

    private void thenDynamicHostPortsSizeIs(int size) {
        assertEquals(size, mapping.getHostPortVariableMap().size());
    }

    private void thenNeedsPropertyUpdate() {
        assertTrue(mapping.needsPropertiesUpdate());
    }

    private void thenHostIpVariableEquals(String key, String ip) {
        assertEquals(ip, mapping.getHostIpVariableMap().get(key));
    }

    private void thenHostPortVariableEquals(String key, Integer port) {
        assertEquals(port, mapping.getHostPortVariableMap().get(key));
    }

    private void thenMapAndVerifyReplacement(String... args) {
        for (int i = 0; i < args.length; i += 2) {
            assertEquals(args[i], StrSubstitutor.replace(args[i + 1], mapping.getHostPortVariableMap()));
        }
    }
    
    private void whenUpdateDynamicMapping(int cPort) {
        Map<String, Container.PortBinding> dynMapping = new HashMap<>();
        dynMapping.put(cPort + "/tcp", null);

        mapping.updateProperties(dynMapping);
    }

    private void whenUpdateDynamicMapping(int cPort, int hPort, String hIp) {
        Map<String, Container.PortBinding> dynMapping = new HashMap<>();
        dynMapping.put(cPort + "/tcp", new Container.PortBinding(hPort, hIp));

        mapping.updateProperties(dynMapping);
    }
}
