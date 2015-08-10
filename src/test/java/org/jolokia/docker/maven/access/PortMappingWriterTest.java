package org.jolokia.docker.maven.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.jolokia.docker.maven.model.Container.PortBinding;
import org.junit.Test;

public class PortMappingWriterTest {

    private Properties properties;
    
    private PortMapping.Writer writer;

    @Test
    public void testWriteGlobalOnly() throws Exception {
        String globalFile = createTmpFile();
        PortMapping mapping = createPortMapping("jolokia.port:8080","18181:8181","127.0.0.1:9090:9090", "127.0.0.1:other.port:5678");
        
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
    public void testWriteImageAndGlobal() throws Exception {
        String imageFile = createTmpFile();
        String globalFile = createTmpFile();
        
        PortMapping mapping1 = createPortMapping("jolokia.port1:8080","18181:8181","127.0.0.1:9090:9090", "1.2.3.4:other.ip1@other.port1:5678");
        PortMapping mapping2 = createPortMapping("jolokia.port2:8080","18182:8181","127.0.0.2:9090:9090", "1.2.3.4:other.ip2@other.port2:5678");
        
        givenAPortMappingWriter(globalFile);
        whenUpdateDynamicMapping(mapping1, "0.0.0.0", 8080, 49900);
        whenUpdateDynamicMapping(mapping1, "1.2.3.4", 5678, 49901);
        whenUpdateDynamicMapping(mapping2, "0.0.0.0", 8080, 49902);
        whenUpdateDynamicMapping(mapping2, "1.2.3.4", 5678, 49903);
   
        whenWritePortMappings(imageFile, mapping1);
        whenWritePortMappings(null, mapping2);
        
        // test all file criteria in order as 'properties' is reset via the exists check

        thenPropsFileExists(globalFile);
        thenPropsSizeIs(6);
        thenPropsContains("jolokia.port1", 49900);
        thenPropsContains("other.port1", 49901);
        thenPropsContains("other.ip1", "1.2.3.4");
        thenPropsContains("jolokia.port2", 49902);
        thenPropsContains("other.port2", 49903);
        thenPropsContains("other.ip2", "1.2.3.4");
        
        thenPropsFileExists(imageFile);
        thenPropsSizeIs(3);
        thenPropsContains("jolokia.port1", 49900);
        thenPropsContains("other.port1", 49901);
        thenPropsContains("other.ip1", "1.2.3.4");
    }
    
    @Test
    public void testWriteImageOnly() throws Exception {
        String imageFile = createTmpFile();
        PortMapping mapping = createPortMapping("jolokia.port:8080","18181:8181","127.0.0.1:9090:9090", "127.0.0.1:other.port:5678");
        
        givenAPortMappingWriter(null);
        whenUpdateDynamicMapping(mapping, "0.0.0.0", 8080, 49900);
        whenUpdateDynamicMapping(mapping, "127.0.0.1", 5678, 49901);
        whenWritePortMappings(imageFile, mapping);
        thenPropsFileExists(imageFile);
        thenPropsSizeIs(2);
        thenPropsContains("jolokia.port", 49900);
        thenPropsContains("other.port", 49901);
    }
    
    private PortMapping createPortMapping(Properties properties, String ... mappings) throws IllegalArgumentException {
        return new PortMapping(Arrays.asList(mappings), properties);
    }
    
    private PortMapping createPortMapping(String ... mappings) throws IllegalArgumentException {
        return createPortMapping(new Properties() ,mappings);
    }
    
    private String createTmpFile() throws IOException {
        File propFile = File.createTempFile("dmpl-", ".properties");
        propFile.deleteOnExit();
        
        return propFile.getAbsolutePath();
    }
    
    private void givenAPortMappingWriter(String globalFile) {
        writer = new PortMapping.Writer(globalFile);
    }
    
    private void thenPropsContains(String variable, Object port) {
        assertEquals(String.valueOf(port), properties.get(variable));
    }
    
    private void thenPropsFileExists(String propertyFile) throws Exception {
        File file = new File(propertyFile);        
        assertTrue(file.exists());
        
        properties = new Properties();
        properties.load(new FileInputStream(file));
    }

    private void thenPropsSizeIs(int size) {
        assertEquals(size, properties.size());
    }
    
    private void whenUpdateDynamicMapping(PortMapping mapping, String ip, int ... ports) {
        Map<String, PortBinding> dynMapping = new HashMap<>();
        for (int i = 0; i < ports.length; i+=2) {
            dynMapping.put(ports[i] + "/tcp", new PortBinding(ports[i+1], ip));
        }
        mapping.updateVariablesWithDynamicPorts(dynMapping);
    }
    
    private void whenWritePortMappings(String imageFile, PortMapping portMapping) throws MojoExecutionException {
        writer.add(portMapping, imageFile);
        writer.write();
    }
}
