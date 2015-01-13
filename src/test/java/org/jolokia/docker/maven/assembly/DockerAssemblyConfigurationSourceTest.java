package org.jolokia.docker.maven.assembly;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.jolokia.docker.maven.config.AssemblyConfiguration;
import org.jolokia.docker.maven.util.MojoParameters;
import org.junit.Before;
import org.junit.Test;

public class DockerAssemblyConfigurationSourceTest {

    private AssemblyConfiguration assemblyConfig;
    private MojoParameters params;
    
    @Before
    public void setup() {
        this.params = new MojoParameters(null, null, null, null, "src/docker", "output/docker");
        
        // set 'dryRun' and 'ignorePermissions' to something other then their defaults
        this.assemblyConfig = new AssemblyConfiguration.Builder()
            .descriptor("assembly.xml")
            .descriptorRef("project")
            .dryRun(true)
            .ignorePermissions(false)
            .build();
    }
    
    @Test
    public void testCreateSource() {
        DockerAssemblyConfigurationSource source = new DockerAssemblyConfigurationSource(params, assemblyConfig);
        
        String[] descriptors = source.getDescriptors();
        String[] descriptorRefs = source.getDescriptorReferences();

        assertEquals(1, descriptors.length);
        assertEquals("src/docker/assembly.xml", descriptors[0]);

        assertEquals(1, descriptorRefs.length);
        assertEquals("project", descriptorRefs[0]);
        
        assertTrue(source.isDryRun());
        assertFalse(source.isIgnorePermissions());
        
        assertTrue(containsOutputDir(source.getOutputDirectory().toString()));
        assertTrue(containsOutputDir(source.getWorkingDirectory().toString()));
        assertTrue(containsOutputDir(source.getTemporaryRootDirectory().toString()));
    }
    
    private boolean containsOutputDir(String path) {
        return path.startsWith("output/docker" + File.separator);
    }
}
