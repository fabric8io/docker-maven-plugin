package io.fabric8.maven.docker.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockSettings;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.config.WatchImageConfiguration;
import io.fabric8.maven.docker.config.WatchMode;
import io.fabric8.maven.docker.model.Image;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import io.fabric8.maven.docker.util.Logger;

@ExtendWith(MockitoExtension.class)
class WatchServiceTest {
    @Mock
    private ArchiveService archiveService;
    @Mock
    private BuildService buildService;
    @Mock
    private DockerAccess dockerAccess;
    @Mock
    private MojoExecutionService mojoExecutionService;
    @Mock
    private QueryService queryService;
    @Mock
    private RunService runService;
    @Mock
    private Logger log;
    private WatchService watchService;


    @BeforeEach
    void setUp() {
        watchService = new WatchService(archiveService, buildService, dockerAccess, mojoExecutionService, queryService, runService, log);
    }

    @Test
    void testWatchCallsGetAllAssemblyConfigurations() {
        // ARRANGE
        ImageConfiguration imageConfiguration = mock(ImageConfiguration.class);
        BuildImageConfiguration buildImageConfiguration = mock(BuildImageConfiguration.class);
        when(imageConfiguration.getBuildConfiguration()).thenReturn(buildImageConfiguration);
        when(runService.getImagesConfigsInOrder(any(), any())).thenReturn(Collections.singletonList(imageConfiguration));

        WatchService.WatchContext watchContext = mock(WatchService.WatchContext.class);
        when(watchContext.getWatchMode()).thenReturn(WatchMode.none);
        BuildService.BuildContext buildContext = mock(BuildService.BuildContext.class);
        List<ImageConfiguration> imageConfigurations = Collections.emptyList();

        // ACT
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                watchService.watch(watchContext, buildContext, imageConfigurations);
            } catch (Exception ignored) {}
        });

        // ASSERT
        verify(buildImageConfiguration, timeout(10_000).times(1)).getAllAssemblyConfigurations();
    }

}