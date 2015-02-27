package org.jolokia.docker.maven.assembly;

import org.jolokia.docker.maven.config.AssemblyConfiguration;
import org.jolokia.docker.maven.config.BuildImageConfiguration;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class DockerAssemblyManagerTest {

    private DockerAssemblyManager assemblyManager;

    @Before
    public void setup() {
        assemblyManager = new DockerAssemblyManager();
    }

    @Test
    public void testNoAssembly() {
        BuildImageConfiguration buildConfig = new BuildImageConfiguration();
        AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();

        DockerFileBuilder builder = assemblyManager.createDockerFileBuilder(buildConfig, assemblyConfig);
        String content = builder.content();

        assertFalse(content.contains("COPY"));
        assertFalse(content.contains("VOLUME"));
    }
}
