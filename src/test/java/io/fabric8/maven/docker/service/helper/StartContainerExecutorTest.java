package io.fabric8.maven.docker.service.helper;

import io.fabric8.maven.docker.access.ContainerCreateConfig;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.ExecException;
import io.fabric8.maven.docker.access.PortMapping;
import io.fabric8.maven.docker.log.LogOutputSpecFactory;
import io.fabric8.maven.docker.model.ContainerDetails;
import io.fabric8.maven.docker.service.ContainerTracker;
import io.fabric8.maven.docker.service.QueryService;
import io.fabric8.maven.docker.service.RunService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.GavLabel;
import io.fabric8.maven.docker.util.JsonFactory;
import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.Mocked;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Test;

import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.LogConfiguration;
import io.fabric8.maven.docker.config.RunImageConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

  @Test
  public void testStartContainers(@Mocked ServiceHub hub, @Mocked DockerAccess dockerAccess, @Mocked ContainerTracker containerTracker, @Mocked Logger log) throws IOException, ExecException {
    // Given
    new Expectations() {{
      dockerAccess.createContainer((ContainerCreateConfig) any, anyString);
      result = "container-name";

      dockerAccess.getContainer(anyString);
      result = new ContainerDetails(JsonFactory.newJsonObject("{\"NetworkSettings\":{\"IPAddress\":\"192.168.1.2\"}}"));

      QueryService queryService = new QueryService(dockerAccess);
      hub.getQueryService();
      result = queryService;

      hub.getRunService();
      result = new RunService(dockerAccess, queryService, containerTracker, new LogOutputSpecFactory(true, true, null), log);
    }};
    Properties projectProps = new Properties();
    StartContainerExecutor startContainerExecutor = new StartContainerExecutor.Builder()
            .serviceHub(hub)
            .projectProperties(projectProps)
            .portMapping(new PortMapping(Collections.emptyList(), projectProps))
            .gavLabel(new GavLabel("io.fabric8:test:0.1.0"))
            .basedir(new File("/tmp/foo"))
            .containerNamePattern("test-")
            .buildTimestamp(new Date())
            .exposeContainerProps("docker.container")
            .imageConfig(new ImageConfiguration.Builder()
                    .name("name")
                    .alias("alias")
                    .runConfig(new RunImageConfiguration.Builder()
                            .build())
                    .build())
            .build();

    // When
    ImmutablePair<String, Properties> result = startContainerExecutor.startContainers();

    // Then
    assertNotNull(result);
    assertEquals("container-name", result.getKey());
    assertEquals("container-name", result.getValue().getProperty("docker.container.alias.id"));
    assertEquals("192.168.1.2", result.getValue().getProperty("docker.container.alias.ip"));
  }
}
