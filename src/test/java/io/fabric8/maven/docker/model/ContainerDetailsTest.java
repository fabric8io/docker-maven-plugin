package io.fabric8.maven.docker.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

class ContainerDetailsTest {

    private Container container;

    private JsonObject json;

    @BeforeEach
    void setup() {
        json = new JsonObject();
    }

    @Test
    void testCustomNetworkIpAddresses() {
        givenNetworkSettings("custom1","1.2.3.4","custom2","5.6.7.8");

        whenCreateContainer();

        thenMappingSize(2);
        thenMappingMatches("custom1","1.2.3.4","custom2","5.6.7.8");
    }

    @Test
    void testEmptyNetworkSettings() {
        givenNetworkSettings();


        whenCreateContainer();

        thenMappingIsNull();
    }

    private void thenMappingIsNull() {
        Assertions.assertNull(container.getCustomNetworkIpAddresses());
    }

    private void thenMappingMatches(String ... args) {
        Map<String,String> addresses = container.getCustomNetworkIpAddresses();
        for (int i = 0; i < args.length; i+=2) {
            Assertions.assertEquals(args[i+1],addresses.get(args[i]));
        }
    }

    private void thenMappingSize(int size) {
        Assertions.assertEquals(container.getCustomNetworkIpAddresses().size(), size);
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
    void testContainerWithMappedPorts() {
        givenAContainerWithMappedPorts();

        whenCreateContainer();

        thenPortBindingSizeIs(2);

        thenMapContainsSpecAndBinding("80/tcp", 32771, "0.0.0.0");
        thenMapContainsSpecAndBinding("52/udp", 32772, "1.2.3.4");
    }

    @Test
    void testContainerWithPorts() {
        givenAContainerWithPorts();
        whenCreateContainer();

        thenPortBindingSizeIs(2);

        thenMapContainsPortSpecOnly("80/tcp");
        thenMapContainsPortSpecOnly("52/udp");
    }

    @Test
    void testContainerWithoutPorts() {
        givenAContainerWithoutPorts();
        whenCreateContainer();
        thenPortBindingSizeIs(0);
    }

    @Test
    void testContainerWithLabels() {
        givenAContainerWithLabels();
        whenCreateContainer();
        thenLabelsSizeIs(2);
        thenLabelsContains("key1", "value1");
        thenLabelsContains("key2", "value2");
    }

    private void thenLabelsContains(String key, String value) {
        Assertions.assertTrue(container.getLabels().containsKey(key));
        Assertions.assertEquals(value, container.getLabels().get(key));
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
    void testCreateContainer()  {
        givenContainerData();
        whenCreateContainer();
        thenValidateContainer();
    }

    @Test
    void testCreateContainerWithEmptyPortBindings()  {
        givenAContainerWithUnboundPorts();
        whenCreateContainer();
       Assertions.assertThrows(PortBindingException.class, () -> container.getPortBindings());
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
        Assertions.assertTrue(container.getPortBindings().containsKey(key));
        Assertions.assertNull(container.getPortBindings().get(key));
    }

    private void thenMapContainsSpecAndBinding(String key, int port, String ip) {
        Assertions.assertTrue(container.getPortBindings().containsKey(key));
        Assertions.assertNotNull(container.getPortBindings().get(key));

        Assertions.assertEquals(ip, container.getPortBindings().get(key).getHostIp());
        Assertions.assertEquals(port, container.getPortBindings().get(key).getHostPort().intValue());
    }

    private void thenLabelsSizeIs(int size) {
        Assertions.assertEquals(size, container.getLabels().size());
    }

    private void thenPortBindingSizeIs(int size) {
        Assertions.assertEquals(size, container.getPortBindings().size());
    }

    private void thenValidateContainer() {
        Assertions.assertEquals(1420559251485L, container.getCreated());
        Assertions.assertEquals("1234AF1234AF", container.getId());
        Assertions.assertEquals("milkman-kindness", container.getName());
        Assertions.assertEquals("9876CE", container.getImage());
        Assertions.assertTrue(container.isRunning());
        Assertions.assertTrue(container.getPortBindings().isEmpty());
    }

    private void whenCreateContainer() {
        container = new ContainerDetails(json);
    }

    @Test
    void testGetIPAddressLegacyFormat() {
        // Legacy format: IPAddress directly under NetworkSettings (Docker < 29, API < 1.44)
        JsonObject networkSettings = new JsonObject();
        networkSettings.addProperty("IPAddress", "172.17.0.2");
        json.add("NetworkSettings", networkSettings);
        
        whenCreateContainer();
        
        Assertions.assertEquals("172.17.0.2", container.getIPAddress());
    }

    @Test
    void testGetIPAddressNewFormat() {
        // New format: IPAddress under Networks (Docker 29+, API 1.44+)
        JsonObject networkSettings = new JsonObject();
        JsonObject networks = new JsonObject();
        JsonObject bridgeNetwork = new JsonObject();
        bridgeNetwork.addProperty("IPAddress", "172.17.0.3");
        networks.add("bridge", bridgeNetwork);
        networkSettings.add("Networks", networks);
        json.add("NetworkSettings", networkSettings);
        
        whenCreateContainer();
        
        Assertions.assertEquals("172.17.0.3", container.getIPAddress());
    }

    @Test
    void testGetIPAddressNewFormatMultipleNetworks() {
        // New format with multiple networks - should return first available IP
        JsonObject networkSettings = new JsonObject();
        JsonObject networks = new JsonObject();
        
        JsonObject customNetwork = new JsonObject();
        customNetwork.addProperty("IPAddress", "192.168.1.10");
        networks.add("custom", customNetwork);
        
        JsonObject bridgeNetwork = new JsonObject();
        bridgeNetwork.addProperty("IPAddress", "172.17.0.4");
        networks.add("bridge", bridgeNetwork);
        
        networkSettings.add("Networks", networks);
        json.add("NetworkSettings", networkSettings);
        
        whenCreateContainer();
        
        // Should return one of the IP addresses (order depends on key iteration)
        String ip = container.getIPAddress();
        Assertions.assertTrue("192.168.1.10".equals(ip) || "172.17.0.4".equals(ip));
    }

    @Test
    void testGetIPAddressNoNetwork() {
        // Test case with no IP address available
        JsonObject networkSettings = new JsonObject();
        json.add("NetworkSettings", networkSettings);
        
        whenCreateContainer();
        
        Assertions.assertNull(container.getIPAddress());
    }


}
