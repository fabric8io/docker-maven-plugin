package org.jolokia.docker.maven.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

public class ContainerListElementTest {

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
        thenMapSizeIs(2);
        thenMapContainsSpecAndBinding("80/tcp", 32771, "0.0.0.0");
        thenMapContainsSpecAndBinding("52/udp", 32772, "1.2.3.4");
    }
    
    @Test
    public void testContaierWithPorts() {
        givenAContaierWithPorts();
        whenCreateContainer();
        thenMapSizeIs(2);
        thenMapContainsPortSpecOnly("80/tcp");
        thenMapContainsPortSpecOnly("52/udp");
    }
    
    @Test
    public void testContainerWithoutPorts() {
        givenAContainerWithoutPorts();
        whenCreateContainer();
        thenMapSizeIs(0);
    }
    
    @Test
    public void testCreateContainer() throws Exception {
        givenContainerData();
        whenCreateContainer();
        thenValidateContainer();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testNoNameInListElement() {
        new ContainersListElement(new JSONObject()).getName();
    }

    private void addToArray(JSONArray array, int index, String key, Object value) {
        array.getJSONObject(index).put(key, value);
    }

    private JSONObject createPortData(int port, String type) {
        JSONObject ports = new JSONObject();
        ports.put("PrivatePort", port);
        ports.put(ContainersListElement.TYPE, type);

        return ports;
    }
    
    private void givenAContaierWithPorts() {
        json.append(Container.PORTS, createPortData(80, "tcp"));
        json.append(Container.PORTS, createPortData(52, "udp"));
    }
    
    private void givenAContainerWithMappedPorts() {
        givenAContaierWithPorts();
        
        JSONArray array = json.getJSONArray(Container.PORTS);

        addToArray(array, 0, ContainersListElement.IP, "0.0.0.0");
        addToArray(array, 0, ContainersListElement.PUBLIC_PORT, 32771);

        addToArray(array, 1, ContainersListElement.IP, "1.2.3.4");
        addToArray(array, 1, ContainersListElement.PUBLIC_PORT, 32772);
    }
    
    private void givenAContainerWithoutPorts() {
        json.put("Ports", Collections.emptyList());
    }

    private void givenContainerData() {
        json.put(Container.CREATED,1420559251485L);
        json.put(Container.ID, "1234AF1234AF");
        json.put(Container.IMAGE, "9876CE");
        json.put(ContainersListElement.STATUS, "Up 16 seconds");
        json.put(Container.PORTS, new JSONArray());
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
    
    private void thenMapSizeIs(int size) {
        assertEquals(size, container.getPortBindings().size());
    }

    private void thenValidateContainer() {
        assertEquals(1420559251485L, container.getCreated());
        assertEquals("1234AF1234AF", container.getId());
        assertEquals("9876CE", container.getImage());
        assertTrue(container.isRunning());
        assertTrue(container.getPortBindings().isEmpty());
    }

    private void whenCreateContainer() {
        container = new ContainersListElement(json);
    }
}
