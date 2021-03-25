package io.fabric8.maven.docker.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ContainerDetailsTest {

    private Container container;

    private JsonObject json;

    @Before
    public void setup() {
        json = new JsonObject();
    }

    @Test
    public void testCustomNetworkIpAddresses() {
        givenNetworkSettings("custom1","1.2.3.4","custom2","5.6.7.8");

        whenCreateContainer();

        thenMappingSize(2);
        thenMappingMatches("custom1","1.2.3.4","custom2","5.6.7.8");
    }

    @Test
    public void testEmptyNetworkSettings() {
        givenNetworkSettings();


        whenCreateContainer();

        thenMappingIsNull();
    }

    private void thenMappingIsNull() {
        assertNull(container.getCustomNetworkIpAddresses());
    }

    private void thenMappingMatches(String ... args) {
        Map<String,String> addresses = container.getCustomNetworkIpAddresses();
        for (int i = 0; i < args.length; i+=2) {
            assertEquals(args[i+1],addresses.get(args[i]));
        }
    }

    private void thenMappingSize(int size) {
        assertEquals(container.getCustomNetworkIpAddresses().size(), size);
    }

    private void givenNetworkSettings(String ... args) {
        JsonObject networkSettings = new JsonObject();
        JsonObject networks = new JsonObject();
        for (int i = 0; i < args.length; i+=2) {
            JsonObject network = new JsonObject();
            network.addProperty("IPAddress",args[i+1]);
            networks.add(args[i], network);
        }
        networkSettings.add("Networks", networks);
        json.add("NetworkSettings", networkSettings);
    }

    @Test
    public void testContainerWithMappedPorts() {
        givenAContainerWithMappedPorts();

        whenCreateContainer();

        thenPortBindingSizeIs(2);

        thenMapContainsSpecAndBinding("80/tcp", 32771, "0.0.0.0");
        thenMapContainsSpecAndBinding("52/udp", 32772, "1.2.3.4");
    }

    @Test
    public void testContainerWithPorts() {
        givenAContainerWithPorts();
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
        JsonObject labels = new JsonObject();
        labels.addProperty("key1", "value1");
        labels.addProperty("key2", "value2");

        JsonObject config = new JsonObject();
        config.add(ContainerDetails.LABELS, labels);

        json.add("Config",config);
    }

    @Test
    public void testCreateContainer() throws Exception {
        givenContainerData();
        whenCreateContainer();
        thenValidateContainer();
    }

    @Test(expected = PortBindingException.class)
    public void testCreateContainerWithEmptyPortBindings() throws Exception {
        givenAContainerWithUnboundPorts();
        whenCreateContainer();
        container.getPortBindings();
    }

    private JsonArray createHostIpAndPort(int port, String ip) {
        JsonObject object = new JsonObject();

        object.addProperty(ContainerDetails.HOST_IP, ip);
        object.addProperty(ContainerDetails.HOST_PORT, String.valueOf(port));

        JsonArray array = new JsonArray();
        array.add(object);

        return array;
    }

    private JsonObject createPortsObject() {
        JsonObject ports = new JsonObject();
        JsonObject networkSettings = new JsonObject();

        networkSettings.add(ContainerDetails.PORTS, ports);
        json.add(ContainerDetails.NETWORK_SETTINGS, networkSettings);

        return ports;
    }

    private void givenAContainerWithPorts() {
        JsonObject ports = createPortsObject();

        ports.add("80/tcp", null);
        ports.add("52/udp", null);
    }

    private void givenAContainerWithMappedPorts() {
        JsonObject ports = createPortsObject();

        ports.add("80/tcp", createHostIpAndPort(32771, "0.0.0.0"));
        ports.add("52/udp", createHostIpAndPort(32772, "1.2.3.4"));
    }

    private void givenAContainerWithoutPorts() {
        json.add(ContainerDetails.NETWORK_SETTINGS, new JsonObject());
    }
    
    private void givenAContainerWithUnboundPorts() {
        JsonObject ports = new JsonObject();
        ports.add("80/tcp", new JsonArray());
        ports.add("52/udp", new JsonArray());
        JsonObject networkSettings = new JsonObject();
        networkSettings.add(ContainerDetails.PORTS, ports);
        json.add(ContainerDetails.NETWORK_SETTINGS, networkSettings);
    }

    private void givenContainerData() {
        json.addProperty(ContainerDetails.CREATED, "2015-01-06T15:47:31.485331387Z");
        json.addProperty(ContainerDetails.ID, "1234AF1234AF");
        json.addProperty(ContainerDetails.NAME, "/milkman-kindness");
        // new JsonObject("{ 'Image': '9876CE'}")
        JsonObject jsonObject1 = new JsonObject();
        jsonObject1.addProperty("Image", "9876CE");
        json.add(ContainerDetails.CONFIG, jsonObject1);

        JsonObject jsonObject2 = new JsonObject();
        jsonObject2.addProperty("Running", "true");
        json.add(ContainerDetails.STATE, jsonObject2);

        json.add(ContainerDetails.NETWORK_SETTINGS, new JsonObject());
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
