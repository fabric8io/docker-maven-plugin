package io.fabric8.maven.docker.service.helper;

import io.fabric8.maven.docker.access.ContainerCreateConfig;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.ExecException;
import io.fabric8.maven.docker.access.PortMapping;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.LogConfiguration;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.log.LogOutputSpecFactory;
import io.fabric8.maven.docker.model.ContainerDetails;
import io.fabric8.maven.docker.service.ContainerTracker;
import io.fabric8.maven.docker.service.QueryService;
import io.fabric8.maven.docker.service.RunService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.service.WaitService;
import io.fabric8.maven.docker.util.GavLabel;
import io.fabric8.maven.docker.util.JsonFactory;
import io.fabric8.maven.docker.util.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;

@ExtendWith(MockitoExtension.class)
class StartContainerExecutorTest {

    @Test
    void getExposedPropertyKeyPart_withoutRunConfig() {

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
        Assertions.assertEquals("alias", actual);
    }

    @Test
    void getExposedPropertyKeyPart_withRunConfig() {

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
        Assertions.assertEquals("key", actual);

    }

    @Test
    void showLogs_withoutRunConfig() {

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
        Assertions.assertFalse(actual);
    }

    @Test
    void showLogs_withoutLogConfigButFollowTrue() {

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
        Assertions.assertTrue(actual);

    }

    @Test
    void showLogs_withLogConfigDisabled() {

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
        Assertions.assertFalse(actual);

    }

    @Test
    void showLogs_withLogConfigEnabled() {

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
        Assertions.assertTrue(actual);

    }

    @Test
    void showLogs_withShowLogsTrue() {

        // Given
        final StartContainerExecutor executor = new StartContainerExecutor.Builder()
            .showLogs("true")
            .build();

        // When
        final boolean actual = executor.showLogs();

        // Then
        Assertions.assertTrue(actual);
    }

    @Test
    void showLogs_withShowLogsMatchRandomImage() {

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
        Assertions.assertFalse(actual);
    }

    @Test
    void showLogs_withShowLogsMatchImage() {

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
        Assertions.assertTrue(actual);
    }

    @Test
    void testStartContainers(@Mock ServiceHub hub, @Mock WaitService waitService, @Mock DockerAccess dockerAccess, @Mock ContainerTracker containerTracker, @Mock Logger log)
        throws IOException, ExecException {
        // Given
        Mockito.doReturn(waitService).when(hub).getWaitService();

        Mockito.doReturn("container-name")
            .when(dockerAccess).createContainer(Mockito.any(ContainerCreateConfig.class), Mockito.anyString());

        Mockito.doReturn(new ContainerDetails(JsonFactory.newJsonObject("{\"NetworkSettings\":{\"IPAddress\":\"192.168.1.2\"}}")))
            .when(dockerAccess).getContainer(Mockito.anyString());

        QueryService queryService = new QueryService(dockerAccess);
        RunService runService = new RunService(dockerAccess, queryService, containerTracker, new LogOutputSpecFactory(true, true, null), log);

        Mockito.doReturn(queryService).when(hub).getQueryService();
        Mockito.doReturn(runService).when(hub).getRunService();

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
        String containerId = startContainerExecutor.startContainer();
        // Then
        Assertions.assertEquals("container-name", containerId);
        Assertions.assertEquals("container-name", projectProps.getProperty("docker.container.alias.id"));
        Assertions.assertEquals("192.168.1.2", projectProps.getProperty("docker.container.alias.ip"));
    }
}
