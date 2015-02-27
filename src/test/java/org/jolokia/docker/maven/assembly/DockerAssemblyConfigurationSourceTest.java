package org.jolokia.docker.maven.assembly;

import java.io.File;

import org.apache.maven.project.MavenProject;
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
        testCreateSource(buildParameters(".", "/src/docker", "/output/docker"));
    }

    @Test
    public void testCreateSourceRelative() {
        testCreateSource(buildParameters(".","src/docker", "output/docker"));
    }

    @Test
    public void testOutputDirHasImage() {
        String image = "image";
        MojoParameters params = buildParameters(".","src/docker", "output/docker");
        DockerAssemblyConfigurationSource source = new DockerAssemblyConfigurationSource(params,
                                                                                         new BuildDirs(params,image),assemblyConfig);
        
        assertTrue(containsDir(image, source.getOutputDirectory()));
        assertTrue(containsDir(image, source.getWorkingDirectory()));
        assertTrue(containsDir(image, source.getTemporaryRootDirectory()));
    }
    
    private MojoParameters buildParameters(String projectDir, String sourceDir, String outputDir) {
        MavenProject mavenProject = new MavenProject();
        mavenProject.setFile(new File(projectDir));
        return new MojoParameters(null, mavenProject, null, null, null, sourceDir, outputDir);
    }

    @Test
    public void testEmptyAssemblyConfig() {
        DockerAssemblyConfigurationSource source = new DockerAssemblyConfigurationSource(
               new MojoParameters(null, null, null, null, null, "/src/docker", "/output/docker"),
               null,null
        );
        assertEquals(0,source.getDescriptors().length);
    }

    private void testCreateSource(MojoParameters params) {
        DockerAssemblyConfigurationSource source =
                new DockerAssemblyConfigurationSource(params, new BuildDirs(params,"image"),assemblyConfig);

        String[] descriptors = source.getDescriptors();
        String[] descriptorRefs = source.getDescriptorReferences();

        assertEquals(1, descriptors.length);
        assertEquals(EnvUtil.prepareAbsoluteSourceDirPath(params, "assembly.xml").getAbsolutePath(), descriptors[0]);

        assertEquals(1, descriptorRefs.length);
        assertEquals("project", descriptorRefs[0]);

        assertFalse(source.isIgnorePermissions());

        String outputDir = params.getOutputDirectory();
        assertTrue(startsWithDir(outputDir, source.getOutputDirectory()));
        assertTrue(startsWithDir(outputDir, source.getWorkingDirectory()));
        assertTrue(startsWithDir(outputDir, source.getTemporaryRootDirectory()));
    }

    private boolean containsDir(String outputDir, File path) {
        return path.toString().contains(outputDir + File.separator);
    }
    
    private boolean startsWithDir(String outputDir, File path) {
        return path.toString().startsWith(outputDir + File.separator);
    }
}
