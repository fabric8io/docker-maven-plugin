package io.fabric8.maven.docker.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContainerListElementTest {

    private Container container;

    private JsonObject json;

    @BeforeEach
    void setup() {
        json = new JsonObject();
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
    void testContainerWithLabels() {
        givenAContainerWithLabels();
        whenCreateContainer();
        thenLabelsSizeIs(2);
        thenLabelsContains("key1", "value1");
        thenLabelsContains("key2", "value2");
    }

    @Test
    void testContainerWithoutLabels() {
        givenContainerData();
        whenCreateContainer();
        thenLabelsSizeIs(0);
    }

    @Test
    void testContainerWithoutPorts() {
        givenAContainerWithoutPorts();
        whenCreateContainer();
        thenPortBindingSizeIs(0);
    }

    @Test
    void testCreateContainer() {
        givenContainerData();
        whenCreateContainer();
        thenValidateContainer();
    }

    @Test
    void testNoNameInListElement() {
        ContainersListElement element = new ContainersListElement(new JsonObject());
        Assertions.assertThrows(UnsupportedOperationException.class, element::getName);
    }

    private void addToArray(JsonArray array, int index, String key, String value) {
        array.get(index).getAsJsonObject().addProperty(key, value);
    }

    private void addToArray(JsonArray array, int index, String key, Integer value) {
        array.get(index).getAsJsonObject().addProperty(key, value);
    }

    private JsonObject createPortData(int port, String type) {
        JsonObject ports = new JsonObject();
        ports.addProperty("PrivatePort", port);
        ports.addProperty(ContainersListElement.TYPE, type);

        return ports;
    }

    private void givenAContainerWithPorts() {
        JsonArray array = new JsonArray();
        array.add(createPortData(80, "tcp"));
        array.add(createPortData(52, "udp"));

        json.add(ContainersListElement.PORTS, array);
    }

    private void givenAContainerWithLabels() {
        JsonObject labels = new JsonObject();
        labels.addProperty("key1", "value1");
        labels.addProperty("key2", "value2");

        json.add(ContainerDetails.LABELS, labels);
    }

    private void givenAContainerWithMappedPorts() {
        givenAContainerWithPorts();

        JsonArray array = json.getAsJsonArray(ContainersListElement.PORTS);

        addToArray(array, 0, ContainersListElement.IP, "0.0.0.0");
        addToArray(array, 0, ContainersListElement.PUBLIC_PORT, 32771);

        addToArray(array, 1, ContainersListElement.IP, "1.2.3.4");
        addToArray(array, 1, ContainersListElement.PUBLIC_PORT, 32772);
    }

    private void givenAContainerWithoutPorts() {
        json.add("Ports", new JsonArray());
    }

    private void givenContainerData() {
        json.addProperty(ContainersListElement.CREATED, 1420559251485L);
        json.addProperty(ContainersListElement.ID, "1234AF1234AF");
        json.addProperty(ContainersListElement.IMAGE, "9876CE");
        json.addProperty(ContainersListElement.STATUS, "Up 16 seconds");
        json.add(ContainersListElement.PORTS, new JsonArray());
    }

    private void thenLabelsContains(String key, String value) {
        Assertions.assertTrue(container.getLabels().containsKey(key));
        Assertions.assertEquals(value, container.getLabels().get(key));
    }

    private void thenLabelsSizeIs(int size) {
        Assertions.assertEquals(size, container.getLabels().size());
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

    private void thenPortBindingSizeIs(int size) {
        Assertions.assertEquals(size, container.getPortBindings().size());
    }

    private void thenValidateContainer() {
        Assertions.assertEquals(1420559251485L, container.getCreated());
        Assertions.assertEquals("1234AF1234AF", container.getId());
        Assertions.assertEquals("9876CE", container.getImage());
        Assertions.assertTrue(container.isRunning());
        Assertions.assertTrue(container.getPortBindings().isEmpty());
    }

    private void whenCreateContainer() {
        container = new ContainersListElement(json);
    }
}
