package io.fabric8.maven.docker.service;

import io.fabric8.maven.docker.util.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class BuildXServiceDockerVersionExternalCommandTest {
  private BuildXService.DockerVersionExternalCommand dockerVersionExternalCommand;

  @BeforeEach
  void setUp() {
    Logger logger = mock(Logger.class);
    dockerVersionExternalCommand = new BuildXService.DockerVersionExternalCommand(logger);
  }

  @Test
  void getArgs() {
    assertArrayEquals(new String[] {"docker", "version", "--format", "'{{.Client.Version}}'"}, dockerVersionExternalCommand.getArgs());
  }

  @Test
  void getVersion() {
    // Given
    dockerVersionExternalCommand.processLine("'24.0.6+azure-2'");
    // When
    String version = dockerVersionExternalCommand.getVersion();
    // Then
    assertEquals("'24.0.6+azure-2'", version);
  }
}
