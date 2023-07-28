package io.fabric8.maven.docker.service;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.io.File;
import java.nio.file.Paths;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.Mockito;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.AuthConfigList;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.ProjectPaths;
import io.fabric8.maven.docker.config.BuildXConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

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
    private final AuthConfigList authConfig = null;

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

    @Test
    void useBuilder_whenConfigProvided_thenAddConfigOptionToBuildX(@TempDir File temporaryFolder) throws MojoExecutionException, IOException {
        // Given
        File dockerBuildKitToml = new File(temporaryFolder, "buildkitd.toml");
        Files.createFile(dockerBuildKitToml.toPath());
        BuildXService.Builder<File> builder = Mockito.mock(BuildXService.Builder.class);
        Mockito.doReturn(temporaryFolder.toPath()).when(buildx).getDockerStateDir(Mockito.any(), Mockito.any());
        givenAnImageConfiguration(new BuildXConfiguration.Builder()
            .dockerStateDir(temporaryFolder.getAbsolutePath())
            .platforms(Arrays.asList(FOREIGN1, FOREIGN2))
            .build());

        // When
        buildx.useBuilder(projectPaths, imageConfig, configuredRegistry, authConfig, buildArchive, builder);

        // Then
        ArgumentCaptor<List<String>> buildXArgCaptor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(builder).useBuilder(buildXArgCaptor.capture(), anyString(), any(), eq(imageConfig), eq(configuredRegistry), eq(buildArchive));
        assertEquals(Arrays.asList("docker", "--config", temporaryFolder.getAbsolutePath(), "buildx"), buildXArgCaptor.getValue());
    }

    @Test
    void useBuilder_whenNoConfigProvided_thenDoNotAddConfigOptionToBuildX() throws MojoExecutionException {
        // Given
        BuildXService.Builder<File> builder = Mockito.mock(BuildXService.Builder.class);
        givenAnImageConfiguration(new BuildXConfiguration.Builder()
            .platforms(Arrays.asList(FOREIGN1, FOREIGN2))
            .build());

        // When
        buildx.useBuilder(projectPaths, imageConfig, configuredRegistry, authConfig, buildArchive, builder);

        // Then
        ArgumentCaptor<List<String>> buildXArgCaptor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(builder).useBuilder(buildXArgCaptor.capture(), anyString(), any(), eq(imageConfig), eq(configuredRegistry), eq(buildArchive));
        assertEquals(Arrays.asList("docker", "buildx"), buildXArgCaptor.getValue());
    }

    private void givenAnImageConfiguration(String... platforms) {
        final BuildXConfiguration buildxConfig = new BuildXConfiguration.Builder()
            .platforms(Arrays.asList(platforms))
            .build();

        givenAnImageConfiguration(buildxConfig);
    }

    private void givenAnImageConfiguration(BuildXConfiguration buildXConfiguration) {
        final BuildImageConfiguration buildImageConfig = new BuildImageConfiguration.Builder()
            .buildx(buildXConfiguration)
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
