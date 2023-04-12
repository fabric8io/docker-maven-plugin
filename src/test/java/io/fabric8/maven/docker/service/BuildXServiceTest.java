package io.fabric8.maven.docker.service;

import java.util.Arrays;
import java.util.List;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.Mockito;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.ProjectPaths;
import io.fabric8.maven.docker.config.BuildXConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class BuildXServiceTest {

    private static final String NATIVE = "linux/amd64";
    private static final String FOREIGN1 = "linux/arm64";
    private static final String FOREIGN2 = "darwin/amd64";

    @Mock
    private DockerAccess dockerAccess;

    @Mock
    private DockerAssemblyManager dockerAssemblyManager;

    @Mock
    private Logger logger;

    @Mock
    private BuildXService.Exec exec;

    @InjectMocks
    @Spy
    private BuildXService buildx;

    private ImageConfiguration imageConfig;

    private final ProjectPaths projectPaths = new ProjectPaths(new File("project-base-dir"), "output-dir");
    private final String configuredRegistry = "configured-registry";
    private final File buildArchive = new File("build-archive");
    private final AuthConfig authConfig = null;

    @BeforeEach
    void setup() throws Exception {

        Mockito.when(dockerAccess.getNativePlatform()).thenReturn(NATIVE);

        Mockito.doNothing().when(buildx).createConfigJson(Mockito.any(), Mockito.any());
        Mockito.doNothing().when(buildx).removeConfigJson(Mockito.any());

        Mockito.doReturn(Paths.get("docker-state-dir")).when(buildx).getDockerStateDir(Mockito.any(), Mockito.any());
        Mockito.doReturn("maven").when(buildx).createBuilder(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void testBuildNativePlatform() throws Exception {
        givenAnImageConfiguration(NATIVE);
        mockBuildX();
        buildx.build(projectPaths, imageConfig, configuredRegistry, authConfig, buildArchive);
        verifyBuildXPlatforms(NATIVE);
    }

    @Test
    void testBuildForeignPlatform() throws Exception {
        givenAnImageConfiguration(FOREIGN1);
        mockBuildX();
        buildx.build(projectPaths, imageConfig, configuredRegistry, authConfig, buildArchive);
        verifyBuildXPlatforms(FOREIGN1);
    }

    @Test
    void testBuildNativePlatformWithForeign() throws Exception {
        givenAnImageConfiguration(NATIVE, FOREIGN1);
        mockBuildX();
        buildx.build(projectPaths, imageConfig, configuredRegistry, authConfig, buildArchive);
        verifyBuildXPlatforms(NATIVE);
    }

    @Test
    void testBuildForeignPlatforms() throws Exception {
        givenAnImageConfiguration(FOREIGN1, FOREIGN2);
        buildx.build(projectPaths, imageConfig, configuredRegistry, authConfig, buildArchive);
        Mockito.verify(buildx, Mockito.times(0)).buildX(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                                                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private void givenAnImageConfiguration(String... platforms) {

        final BuildXConfiguration buildxConfig = new BuildXConfiguration.Builder()
            .platforms(Arrays.asList(platforms))
            .build();

        final BuildImageConfiguration buildImageConfig = new BuildImageConfiguration.Builder()
            .buildx(buildxConfig)
            .build();

        imageConfig = new ImageConfiguration.Builder()
            .name("build-image")
            .buildConfig(buildImageConfig)
            .build();
    }

    private void mockBuildX() throws Exception {
        Mockito.doNothing().when(buildx).buildX(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                                                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private void verifyBuildXPlatforms(String... platforms) throws Exception {
        final List<String> expect = Arrays.asList(platforms);
        Mockito.verify(buildx).buildX(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                                      Mockito.argThat(l -> expect.equals(l)), Mockito.any(), Mockito.any());
    }
}
