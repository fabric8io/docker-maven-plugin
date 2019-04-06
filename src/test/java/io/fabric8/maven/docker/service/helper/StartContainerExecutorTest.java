package io.fabric8.maven.docker.service.helper;

import org.junit.Test;

import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.LogConfiguration;
import io.fabric8.maven.docker.config.RunImageConfiguration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StartContainerExecutorTest {

  @Test
  public void getExposedPropertyKeyPart_withoutRunConfig() {

    // Given
    final ImageConfiguration imageConfig = new ImageConfiguration.Builder()
        .name("name")
        .alias("alias")
        .build();

    final StartContainerExecutor executor = new StartContainerExecutor.Builder()
        .imageConfig(imageConfig)
        .build();

    // When
    final String actual = executor.getExposedPropertyKeyPart();

    // Then
    assertEquals("alias", actual);

  }


  @Test
  public void getExposedPropertyKeyPart_withRunConfig() {

    // Given
    final RunImageConfiguration runConfig = new RunImageConfiguration.Builder()
        .exposedPropertyKey("key")
        .build();

    final ImageConfiguration imageConfig = new ImageConfiguration.Builder()
        .name("name")
        .alias("alias")
        .runConfig(runConfig)
        .build();

    final StartContainerExecutor executor = new StartContainerExecutor.Builder()
        .imageConfig(imageConfig)
        .build();

    // When
    final String actual = executor.getExposedPropertyKeyPart();

    // Then
    assertEquals("key", actual);

  }

  @Test
  public void showLogs_withoutRunConfig() {

    // Given
    final ImageConfiguration imageConfig = new ImageConfiguration.Builder()
        .name("name")
        .alias("alias")
        .build();

    final StartContainerExecutor executor = new StartContainerExecutor.Builder()
        .imageConfig(imageConfig)
        .build();

    // When
    final boolean actual = executor.showLogs();

    // Then
    assertFalse(actual);

  }

  @Test
  public void showLogs_withoutLogConfigButFollowTrue() {

    // Given
    final RunImageConfiguration runConfig = new RunImageConfiguration.Builder()
        .exposedPropertyKey("key")
        .build();

    final ImageConfiguration imageConfig = new ImageConfiguration.Builder()
        .name("name")
        .alias("alias")
        .runConfig(runConfig)
        .build();

    final StartContainerExecutor executor = new StartContainerExecutor.Builder()
        .imageConfig(imageConfig)
        .follow(true)
        .build();

    // When
    final boolean actual = executor.showLogs();

    // Then
    assertTrue(actual);

  }

  @Test
  public void showLogs_withLogConfigDisabled() {

    // Given
    final LogConfiguration logConfig = new LogConfiguration.Builder()
        .enabled(false)
        .build();

    final RunImageConfiguration runConfig = new RunImageConfiguration.Builder()
        .exposedPropertyKey("key")
        .log(logConfig)
        .build();

    final ImageConfiguration imageConfig = new ImageConfiguration.Builder()
        .name("name")
        .alias("alias")
        .runConfig(runConfig)
        .build();

    final StartContainerExecutor executor = new StartContainerExecutor.Builder()
        .imageConfig(imageConfig)
        .build();

    // When
    final boolean actual = executor.showLogs();

    // Then
    assertFalse(actual);

  }

  @Test
  public void showLogs_withLogConfigEnabled() {

    // Given
    final LogConfiguration logConfig = new LogConfiguration.Builder()
        .enabled(true)
        .build();

    final RunImageConfiguration runConfig = new RunImageConfiguration.Builder()
        .exposedPropertyKey("key")
        .log(logConfig)
        .build();

    final ImageConfiguration imageConfig = new ImageConfiguration.Builder()
        .name("name")
        .alias("alias")
        .runConfig(runConfig)
        .build();

    final StartContainerExecutor executor = new StartContainerExecutor.Builder()
        .imageConfig(imageConfig)
        .build();

    // When
    final boolean actual = executor.showLogs();

    // Then
    assertTrue(actual);

  }

  @Test
  public void showLogs_withShowLogsTrue() {

    // Given
    final StartContainerExecutor executor = new StartContainerExecutor.Builder()
        .showLogs("true")
        .build();

    // When
    final boolean actual = executor.showLogs();

    // Then
    assertTrue(actual);
  }

  @Test
  public void showLogs_withShowLogsMatchRandomImage() {

    // Given
    final ImageConfiguration imageConfig = new ImageConfiguration.Builder()
        .name("name")
        .alias("alias")
        .build();

    final StartContainerExecutor executor = new StartContainerExecutor.Builder()
        .showLogs("some_random_string")
        .imageConfig(imageConfig)
        .build();

    // When
    final boolean actual = executor.showLogs();

    // Then
    assertFalse(actual);
  }

  @Test
  public void showLogs_withShowLogsMatchImage() {

    // Given
    final ImageConfiguration imageConfig = new ImageConfiguration.Builder()
        .name("name")
        .alias("alias")
        .build();

    final StartContainerExecutor executor = new StartContainerExecutor.Builder()
        .showLogs("name, alias")
        .imageConfig(imageConfig)
        .build();

    // When
    final boolean actual = executor.showLogs();

    // Then
    assertTrue(actual);
  }
}
