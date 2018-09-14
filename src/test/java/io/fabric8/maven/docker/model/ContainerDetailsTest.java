package io.fabric8.maven.docker.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static io.fabric8.maven.docker.util.JsonUtils.toJSONObject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ContainerDetailsTest {

    private Container container;

    private JSONObject json;

    @Before
    public void setup() {
        json = new JSONObject();
    }

    @Test
    public void testCustomNetworkIpAddresses() throws JSONException {
        givenNetworkSettings("custom1","1.2.3.4","custom2","5.6.7.8");

        whenCreateContainer();

        thenMappingSize(2);
        thenMappingMatches("custom1","1.2.3.4","custom2","5.6.7.8");
    }

    @Test
    public void testEmptyNetworkSettings() throws JSONException {
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

    private void givenNetworkSettings(String ... args) throws JSONException {
        JSONObject networkSettings = new JSONObject();
        JSONObject networks = new JSONObject();
        for (int i = 0; i < args.length; i+=2) {
            JSONObject network = new JSONObject();
            network.put("IPAddress",args[i+1]);
            networks.put(args[i],network);
        }
        networkSettings.put("Networks", networks);
        json.put("NetworkSettings", networkSettings);
    }

    @Test
    public void testContainerWithMappedPorts() throws JSONException {
        givenAContainerWithMappedPorts();

        whenCreateContainer();

        thenPortBindingSizeIs(2);
        thenMapContainsSpecAndBinding("80/tcp", 32771, "0.0.0.0");
        thenMapContainsSpecAndBinding("52/udp", 32772, "1.2.3.4");
    }

    @Test
    public void testContainerWithPorts() throws JSONException {
        givenAContainerWithPorts();
        whenCreateContainer();

        thenPortBindingSizeIs(2);
        thenMapContainsPortSpecOnly("80/tcp");
        thenMapContainsPortSpecOnly("52/udp");
    }

    @Test
    public void testContainerWithoutPorts() throws JSONException {
        givenAContainerWithoutPorts();
        whenCreateContainer();
        thenPortBindingSizeIs(0);
    }

    @Test
    public void testContainerWithLabels() throws JSONException {
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

    private void givenAContainerWithLabels() throws JSONException {
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

    private JSONArray createHostIpAndPort(int port, String ip) throws JSONException {
        JSONObject object = new JSONObject();

        object.put(ContainerDetails.HOST_IP, ip);
        object.put(ContainerDetails.HOST_PORT, String.valueOf(port));

        JSONArray array = new JSONArray();
        array.put(object);

        return array;
    }

    private JSONObject createPortsObject() throws JSONException {
        JSONObject ports = new JSONObject();
        JSONObject networkSettings = new JSONObject();

        networkSettings.put(ContainerDetails.PORTS, ports);
        json.put(ContainerDetails.NETWORK_SETTINGS, networkSettings);

        return ports;
    }

    private void givenAContainerWithPorts() throws JSONException {
        JSONObject ports = createPortsObject();

        ports.put("80/tcp", JSONObject.NULL);
        ports.put("52/udp", JSONObject.NULL);
    }

    private void givenAContainerWithMappedPorts() throws JSONException {
        JSONObject ports = createPortsObject();

        ports.put("80/tcp", createHostIpAndPort(32771, "0.0.0.0"));
        ports.put("52/udp", createHostIpAndPort(32772, "1.2.3.4"));
    }

    private void givenAContainerWithoutPorts() throws JSONException {
        json.put(ContainerDetails.NETWORK_SETTINGS, JSONObject.NULL);
    }

    private void givenContainerData() throws JSONException {
        json.put(ContainerDetails.CREATED, "2015-01-06T15:47:31.485331387Z");
        json.put(ContainerDetails.ID, "1234AF1234AF");
        json.put(ContainerDetails.NAME, "/milkman-kindness");
        json.put(ContainerDetails.CONFIG, toJSONObject("{ 'Image': '9876CE'}"));
        json.put(ContainerDetails.STATE, toJSONObject("{'Running' : true }"));
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
