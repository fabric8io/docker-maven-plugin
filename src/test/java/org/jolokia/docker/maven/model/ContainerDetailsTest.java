package org.jolokia.docker.maven.model;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ContainerDetailsTest {

    private Container container;

    private JSONObject json;

    @Before
    public void setup() {
        json = new JSONObject();
    }

    @Test
    public void testContaierWithMappedPorts() {
        givenAContainerWithMappedPorts();
        whenCreateContainer();
        thenPortBindingSizeIs(2);
        thenMapContainsSpecAndBinding("80/tcp", 32771, "0.0.0.0");
        thenMapContainsSpecAndBinding("52/udp", 32772, "1.2.3.4");
    }

    @Test
    public void testContaierWithPorts() {
        givenAContaierWithPorts();
        whenCreateContainer();
        thenPortBindingSizeIs(2);
        thenMapContainsPortSpecOnly("80/tcp");
        thenMapContainsPortSpecOnly("52/udp");
    }

    @Test
    public void testContainerWithoutPorts() {
        givenAContainerWithoutPorts();
        whenCreateContainer();
        thenPortBindingSizeIs(0);
    }

    @Test
    public void testContainerWithLabels() {
        givenAContainerWithLabels();
        whenCreateContainer();
        thenLabelsSizeIs(2);
        thenLabelsContains("key1", "value1");
        thenLabelsContains("key2", "value2");
    }
    
    private void thenLabelsContains(String key, String value) {
        assertTrue(container.getLabels().containsKey(key));
        assertEquals(value, container.getLabels().get(key));
    }
    
    private void givenAContainerWithLabels() {
        JSONObject labels = new JSONObject();
        labels.put("key1", "value1");
        labels.put("key2", "value2");

        JSONObject config = new JSONObject();
        config.put(ContainerDetails.LABELS, labels);

        json.put("Config",config);
    }
    
    @Test
    public void testCreateContainer() throws Exception {
        givenContainerData();
        whenCreateContainer();
        thenValidateContainer();
    }
    
    private JSONArray createHostIpAndPort(int port, String ip) {
        JSONObject object = new JSONObject();
        
        object.put(ContainerDetails.HOST_IP, ip);
        object.put(ContainerDetails.HOST_PORT, String.valueOf(port));
        
        JSONArray array = new JSONArray();
        array.put(object);
        
        return array;
    }
    
    private JSONObject createPortsObject() {
        JSONObject ports = new JSONObject();
        JSONObject networkSettings = new JSONObject();
        
        networkSettings.put(ContainerDetails.PORTS, ports);
        json.put(ContainerDetails.NETWORK_SETTINGS, networkSettings);
        
        return ports;
    }
    
    private void givenAContaierWithPorts() {
        JSONObject ports = createPortsObject();
        
        ports.put("80/tcp", JSONObject.NULL);
        ports.put("52/udp", JSONObject.NULL);
    }
   
    private void givenAContainerWithMappedPorts() {
        JSONObject ports = createPortsObject();
        
        ports.put("80/tcp", createHostIpAndPort(32771, "0.0.0.0"));
        ports.put("52/udp", createHostIpAndPort(32772, "1.2.3.4"));
    }
    
    private void givenAContainerWithoutPorts() {
        json.put(ContainerDetails.NETWORK_SETTINGS, JSONObject.NULL);
    }

    private void givenContainerData() {
        json.put(ContainerDetails.CREATED, "2015-01-06T15:47:31.485331387Z");
        json.put(ContainerDetails.ID, "1234AF1234AF");
        json.put(ContainerDetails.NAME, "/milkman-kindness");
        json.put(ContainerDetails.CONFIG, new JSONObject("{ 'Image': '9876CE'}"));
        json.put(ContainerDetails.STATE, new JSONObject("{'Running' : true }"));
        json.put(ContainerDetails.NETWORK_SETTINGS, JSONObject.NULL);
    }

    private void thenMapContainsPortSpecOnly(String key) {
        assertTrue(container.getPortBindings().containsKey(key));
        assertNull(container.getPortBindings().get(key));
    }

    private void thenMapContainsSpecAndBinding(String key, int port, String ip) {
        assertTrue(container.getPortBindings().containsKey(key));
        assertNotNull(container.getPortBindings().get(key));

        assertEquals(ip, container.getPortBindings().get(key).getHostIp());
        assertEquals(port, container.getPortBindings().get(key).getHostPort().intValue());
    }

    private void thenLabelsSizeIs(int size) {
        assertEquals(size, container.getLabels().size());   
    }
    
    private void thenPortBindingSizeIs(int size) {
        assertEquals(size, container.getPortBindings().size());
    }

    private void thenValidateContainer() {
        assertEquals(1420559251485L, container.getCreated());
        assertEquals("1234AF1234AF", container.getId());
        assertEquals("milkman-kindness", container.getName());
        assertEquals("9876CE", container.getImage());
        assertTrue(container.isRunning());
        assertTrue(container.getPortBindings().isEmpty());
    }

    private void whenCreateContainer() {
        container = new ContainerDetails(json);
    }
}
