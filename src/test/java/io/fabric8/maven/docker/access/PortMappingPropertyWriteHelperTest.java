package io.fabric8.maven.docker.access;

import io.fabric8.maven.docker.model.Container;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

class PortMappingPropertyWriteHelperTest {

    private Properties loadedProperties;
    private Properties projProperties;

    private PortMapping.PropertyWriteHelper propertyWriteHelper;

    @BeforeEach
    void setup() {
        projProperties = new Properties();
    }

    @Test
    void testWriteGlobalOnly() throws IOException {
        String globalFile = createTmpFile();
        PortMapping mapping = createPortMapping("jolokia.port:8080", "18181:8181", "127.0.0.1:9090:9090", "127.0.0.1:other.port:5678");

        givenAPortMappingWriter(globalFile);
        whenUpdateDynamicMapping(mapping, "0.0.0.0", 8080, 49900);
        whenUpdateDynamicMapping(mapping, "127.0.0.1", 5678, 49901);
        whenWritePortMappings(null, mapping);
        thenPropsFileExists(globalFile);
        thenPropsSizeIs(2);
        thenPropsContains("jolokia.port", 49900);
        thenPropsContains("other.port", 49901);
    }

    @Test
    void testWriteImageAndGlobal() throws IOException {
        String imageFile = createTmpFile();
        String globalFile = createTmpFile();

        PortMapping mapping1 = createPortMapping("jolokia.port1:8080", "18181:8181", "127.0.0.1:9090:9090", "+other.ip1:other.port1:5678");
        PortMapping mapping2 = createPortMapping("jolokia.port2:8080", "18182:8181", "127.0.0.2:9090:9090", "+other.ip2:other.port2:5678");
        PortMapping mapping3 = createPortMapping("+other.ip3:other.port3:5678");
        
        givenADockerHostAddress("5.6.7.8");
        givenAHostIpProperty("other.ip1", "1.2.3.4");
        givenAHostIpProperty("other.ip2", "1.2.3.4");
        givenAPortMappingWriter(globalFile);

        whenUpdateDynamicMapping(mapping1, "0.0.0.0", 8080, 49900);
        whenUpdateDynamicMapping(mapping1, "1.2.3.4", 5678, 49901);
        whenUpdateDynamicMapping(mapping2, "0.0.0.0", 8080, 49902);
        whenUpdateDynamicMapping(mapping2, "1.2.3.4", 5678, 49903);
        whenUpdateDynamicMapping(mapping3, "0.0.0.0", 5678, 49904);
        
        whenWritePortMappings(imageFile, mapping1);
        whenWritePortMappings(null, mapping2);
        whenWritePortMappings(null, mapping3);

        // test all file criteria in order as 'properties' is reset via the exists check

        thenPropsFileExists(globalFile);
        thenPropsSizeIs(8);
        thenPropsContains("jolokia.port1", 49900);
        thenPropsContains("other.port1", 49901);
        thenPropsContains("other.ip1", "1.2.3.4");
        thenPropsContains("jolokia.port2", 49902);
        thenPropsContains("other.port2", 49903);
        thenPropsContains("other.ip2", "1.2.3.4");
        thenPropsContains("other.port3", 49904);
        thenPropsContains("other.ip3", "5.6.7.8");
        
        thenPropsFileExists(imageFile);
        thenPropsSizeIs(3);
        thenPropsContains("jolokia.port1", 49900);
        thenPropsContains("other.port1", 49901);
        thenPropsContains("other.ip1", "1.2.3.4");
    }

    @Test
    void testWriteCreatePaths() throws IOException {
        File dir = Files.createTempDirectory("dmp-").toFile();
        Assertions.assertTrue(dir.delete());
        String globalFile = Paths.get(dir.getAbsolutePath(), "dmp-tmp.properties").toFile().getAbsolutePath();
        PortMapping mapping = createPortMapping("jolokia.port:8080", "18181:8181", "127.0.0.1:9090:9090", "127.0.0.1:other.port:5678");

        // check that we can write on non-existant path with create set to "true"
        givenAPortMappingWriter(globalFile);
        whenUpdateDynamicMapping(mapping, "0.0.0.0", 8080, 49900);
        whenUpdateDynamicMapping(mapping, "127.0.0.1", 5678, 49901);
        whenWritePortMappings(null, mapping);
        thenPropsFileExists(globalFile);
        thenPropsSizeIs(2);
        thenPropsContains("jolokia.port", 49900);
        thenPropsContains("other.port", 49901);

        // Check that we can still write in an existing path
        String globalFile2 = Paths.get(dir.getAbsolutePath(), "dmp-tmp2.properties").toFile().getAbsolutePath();
        givenAPortMappingWriter(globalFile2);
        whenUpdateDynamicMapping(mapping, "0.0.0.0", 8080, 49900);
        whenUpdateDynamicMapping(mapping, "127.0.0.1", 5678, 49901);
        whenWritePortMappings(null, mapping);
        thenPropsFileExists(globalFile2);
        thenPropsSizeIs(2);
        thenPropsContains("jolokia.port", 49900);
        thenPropsContains("other.port", 49901);


    }

    private void givenADockerHostAddress(String host) {
        projProperties.setProperty("docker.host.address", host);
    }
    
    
    @Test
    void testWriteImageOnly() throws Exception {
        String imageFile = createTmpFile();
        PortMapping mapping = createPortMapping("jolokia.port:8080", "18181:8181", "127.0.0.1:9090:9090", "127.0.0.1:other.port:5678");

        givenAPortMappingWriter(null);
        whenUpdateDynamicMapping(mapping, "0.0.0.0", 8080, 49900);
        whenUpdateDynamicMapping(mapping, "127.0.0.1", 5678, 49901);
        whenWritePortMappings(imageFile, mapping);
        thenPropsFileExists(imageFile);
        thenPropsSizeIs(2);
        thenPropsContains("jolokia.port", 49900);
        thenPropsContains("other.port", 49901);
    }

    private PortMapping createPortMapping(Properties properties, String... mappings) throws IllegalArgumentException {
        return new PortMapping(Arrays.asList(mappings), properties);
    }

    private PortMapping createPortMapping(String... mappings) throws IllegalArgumentException {
        return createPortMapping(projProperties, mappings);
    }

    private String createTmpFile() throws IOException {
        File propFile = File.createTempFile("dmpl-", ".properties");
        propFile.deleteOnExit();

        return propFile.getAbsolutePath();
    }

    private void givenAHostIpProperty(String property, String hostIp) {
        projProperties.put(property, hostIp);
    }

    private void givenAPortMappingWriter(String globalFile) {
        propertyWriteHelper = new PortMapping.PropertyWriteHelper(globalFile);
    }

    private void thenPropsContains(String variable, Object port) {
        Assertions.assertEquals(String.valueOf(port), loadedProperties.get(variable));
    }

    private void thenPropsFileExists(String propertyFile) throws IOException {
        File file = new File(propertyFile);
        Assertions.assertTrue(file.exists());

        loadedProperties = new Properties();
        loadedProperties.load(Files.newInputStream(file.toPath()));
    }

    private void thenPropsSizeIs(int size) {
        Assertions.assertEquals(size, loadedProperties.size());
    }

    private void whenUpdateDynamicMapping(PortMapping mapping, String ip, int... ports) {
        Map<String, Container.PortBinding> dynMapping = new HashMap<>();
        for (int i = 0; i < ports.length; i += 2) {
            dynMapping.put(ports[i] + "/tcp", new Container.PortBinding(ports[i + 1], ip));
        }
        mapping.updateProperties(dynMapping);
    }

    private void whenWritePortMappings(String imageFile, PortMapping portMapping) throws IOException {
        propertyWriteHelper.add(portMapping, imageFile);
        propertyWriteHelper.write();
    }
}
