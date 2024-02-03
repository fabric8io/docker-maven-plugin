package io.fabric8.maven.docker.service;

import io.fabric8.maven.docker.assembly.AssemblyFiles;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveServiceTest {

    @Mock
    private DockerAssemblyManager dockerAssemblyManager;

    @Mock
    private Logger log;

    private ArchiveService archiveService;

    @BeforeEach
    void setUp() {
        archiveService = new ArchiveService(dockerAssemblyManager, null);
    }

    @Test
    void testGetAssemblyFiles() throws Exception {
        // ARRANGE
        when(dockerAssemblyManager.getAssemblyFiles(any(), any(), any(), any())).thenReturn(mock(AssemblyFiles.class));
        AssemblyConfiguration assemblyConfiguration = new AssemblyConfiguration();
        BuildImageConfiguration build = new BuildImageConfiguration.Builder().assembly(assemblyConfiguration).build();
        ImageConfiguration imageConfiguration = new ImageConfiguration.Builder().buildConfig(build).build();

        // ACT
        AssemblyFiles assemblyFiles = archiveService.getAssemblyFiles(imageConfiguration, assemblyConfiguration.getName(), mock(MojoParameters.class));

        // ASSERT
        Assertions.assertNotNull(assemblyFiles);
    }
}