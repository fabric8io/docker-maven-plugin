package org.jolokia.docker.maven.assembly;

import java.io.File;

import org.jolokia.docker.maven.config.AssemblyConfiguration;
import org.jolokia.docker.maven.util.EnvUtil;
import org.jolokia.docker.maven.util.MojoParameters;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DockerAssemblyConfigurationSourceTest {

    private AssemblyConfiguration assemblyConfig;

    @Before
    public void setup() {
        // set 'ignorePermissions' to something other then default
        this.assemblyConfig = new AssemblyConfiguration.Builder()
                .descriptor("assembly.xml")
                .descriptorRef("project")
                .ignorePermissions(false)
                .build();
    }

    @Test
    public void testCreateSourceAbsolute() {
        testCreateSource(new MojoParameters(null, null, null, null, "/src/docker", "/output/docker"));
    }

    @Test
    public void testCreateSourceRelative() {
        testCreateSource(new MojoParameters(null, null, null, null, "src/docker", "output/docker"));
    }

    private void testCreateSource(MojoParameters params) {
        DockerAssemblyConfigurationSource source = new DockerAssemblyConfigurationSource(params, assemblyConfig);

        String[] descriptors = source.getDescriptors();
        String[] descriptorRefs = source.getDescriptorReferences();

        assertEquals(1, descriptors.length);
        assertEquals(EnvUtil.prepareAbsolutePath(params.getSourceDirectory(),"assembly.xml").getAbsolutePath(), descriptors[0]);

        assertEquals(1, descriptorRefs.length);
        assertEquals("project", descriptorRefs[0]);

        assertFalse(source.isIgnorePermissions());

        String outputDir = params.getOutputDirectory();
        assertTrue(containsOutputDir(outputDir, source.getOutputDirectory().toString()));
        assertTrue(containsOutputDir(outputDir, source.getWorkingDirectory().toString()));
        assertTrue(containsOutputDir(outputDir, source.getTemporaryRootDirectory().toString()));
    }

    private boolean containsOutputDir(String outputDir, String path) {
        return path.startsWith(outputDir + File.separator);
    }
}
