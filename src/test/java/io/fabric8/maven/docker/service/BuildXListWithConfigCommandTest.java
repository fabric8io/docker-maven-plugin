package io.fabric8.maven.docker.service;

import io.fabric8.maven.docker.util.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class BuildXListWithConfigCommandTest {
  @TempDir
  private File temporaryFolder;

  private BuildXService.BuildXListWithConfigCommand buildXListWithConfigCommand;

  @BeforeEach
  void setUp() {
    Logger logger = mock(Logger.class);
    buildXListWithConfigCommand = new BuildXService.BuildXListWithConfigCommand(logger, temporaryFolder.toPath());
  }

  @Test
  void getArgs() {
    assertArrayEquals(new String[] {"docker", "--config", temporaryFolder.getAbsolutePath(), "buildx", "ls"}, buildXListWithConfigCommand.getArgs());
  }

  @Test
  void isSuccessFul() {
    // Given
    assertTrue(buildXListWithConfigCommand.isSuccessFul());
  }
}