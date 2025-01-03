package io.fabric8.maven.docker.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.AuthConfigList;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.BuildXConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.ProjectPaths;

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

    @TempDir
    private File temporaryFolder;

    private ImageConfiguration imageConfig;

    private ProjectPaths projectPaths;
    private final String configuredRegistry = "configured-registry";
    private final File buildArchive = new File("build-archive");
    private AuthConfigList authConfigList;

    @BeforeEach
    void setup() throws Exception {

        when(dockerAccess.getNativePlatform()).thenReturn(NATIVE);

        Mockito.doNothing().when(buildx).createConfigJson(Mockito.any(), Mockito.any());
        Mockito.doNothing().when(buildx).removeConfigJson(Mockito.any());

        Mockito.doReturn(Paths.get(temporaryFolder.getPath(), "docker-state-dir")).when(buildx).getDockerStateDir(Mockito.any(), Mockito.any());
        Mockito.doReturn("maven").when(buildx).createBuilder(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        authConfigList = null;
        projectPaths = new ProjectPaths(new File(temporaryFolder, "project-base-dir"), "output-dir");
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void testBuildNativePlatform() throws Exception {
        givenAnImageConfiguration(NATIVE);
        mockBuildX();
        buildx.build(projectPaths, imageConfig, configuredRegistry, authConfigList, buildArchive);
        verifyBuildXPlatforms(NATIVE);
    }

    @Test
    void testBuildForeignPlatform() throws Exception {
        givenAnImageConfiguration(FOREIGN1);
        mockBuildX();
        buildx.build(projectPaths, imageConfig, configuredRegistry, authConfigList, buildArchive);
        verifyBuildXPlatforms(FOREIGN1);
    }

    @Test
    void testBuildNativePlatformWithForeign() throws Exception {
        givenAnImageConfiguration(NATIVE, FOREIGN1);
        mockBuildX();
        buildx.build(projectPaths, imageConfig, configuredRegistry, authConfigList, buildArchive);
        verifyBuildXPlatforms(NATIVE);
    }

    @Test
    void testNetworkIsPropagatedToBuildx() throws Exception {

        //Given
        buildConfigUsingBuildx(temporaryFolder, (buildX, buildImage) -> buildImage.network("host"));

        // When
        buildx.build(projectPaths, imageConfig, configuredRegistry, authConfigList, buildArchive);

        //Then
        verifyBuildXArgumentPresentInExec("--network=host");
    }

    @Test
    void testNoCacheIsPropagatedToBuildx() throws Exception {

        //Given
        buildConfigUsingBuildx(temporaryFolder, (buildX, buildImage) -> buildImage.noCache(true));

        // When
        buildx.build(projectPaths, imageConfig, configuredRegistry, authConfigList, buildArchive);

        //Then
        verifyBuildXArgumentPresentInExec("--no-cache");
    }

    @Test
    void testBuildXCacheFromIsNotPresentIfNotProvided() throws Exception {

        //Given
        buildConfigUsingBuildx(temporaryFolder, (buildX, buildImage) -> buildX.cacheFrom(null));

        // When
        buildx.build(projectPaths, imageConfig, configuredRegistry, authConfigList, buildArchive);

        //Then
        verifyBuildXArgumentNotPresentInExec("--cache-from");
    }

    @Test
    void testBuildXCacheFromIsPresentIfProvided(@TempDir File temporaryFolder) throws Exception {

        //Given
        buildConfigUsingBuildx(temporaryFolder, (buildX, buildImage) -> buildX.cacheFrom("cacheFromSpec"));

        // When
        buildx.build(projectPaths, imageConfig, configuredRegistry, authConfigList, buildArchive);

        //Then
        verifyBuildXArgumentPresentInExec("--cache-from=cacheFromSpec");
    }

    @Test
    void testBuildXCacheToIsNotPresentIfNotProvided() throws Exception {

        //Given
        buildConfigUsingBuildx(temporaryFolder, (buildX, buildImage) -> buildX.cacheTo(null));

        // When
        buildx.build(projectPaths, imageConfig, configuredRegistry, authConfigList, buildArchive);

        //Then
        verifyBuildXArgumentNotPresentInExec("--cache-to");
    }

    @Test
    void testBuildXCacheToIsPresentIfProvided() throws Exception {

        //Given
        buildConfigUsingBuildx(temporaryFolder, (buildX, buildImage) -> buildX.cacheTo("cacheToSpec"));

        // When
        buildx.build(projectPaths, imageConfig, configuredRegistry, authConfigList, buildArchive);

        //Then
        verifyBuildXArgumentPresentInExec("--cache-to=cacheToSpec");
    }

    private void buildConfigUsingBuildx(File temporaryFolder, BiConsumer<BuildXConfiguration.Builder, BuildImageConfiguration.Builder> spec) {
        final BuildXConfiguration.Builder buildXConfigurationBuilder = new BuildXConfiguration.Builder()
                .dockerStateDir(temporaryFolder.getAbsolutePath())
                .platforms(Arrays.asList(NATIVE));
        final BuildImageConfiguration.Builder buildImageConfigBuilder = new BuildImageConfiguration.Builder();
        spec.accept(buildXConfigurationBuilder, buildImageConfigBuilder);
        final BuildXConfiguration buildxConfig = buildXConfigurationBuilder.build();
        final BuildImageConfiguration buildImageConfig = buildImageConfigBuilder
                .buildx(buildxConfig)
                .build();
        imageConfig = new ImageConfiguration.Builder()
                .name("build-image")
                .buildConfig(buildImageConfig)
                .build();
    }

    @Test
    void testBuildForeignPlatforms() throws Exception {
        givenAnImageConfiguration(FOREIGN1, FOREIGN2);
        buildx.build(projectPaths, imageConfig, configuredRegistry, authConfigList, buildArchive);
        Mockito.verify(buildx, Mockito.times(0)).buildX(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                                                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void useBuilder_whenConfiguredRegistryAbsentInDockerRegistry_thenAddConfigOptionToBuildX() throws MojoExecutionException, IOException {
        // Given
        BuildXService.Builder<File> builder = Mockito.mock(BuildXService.Builder.class);
        Mockito.doReturn(temporaryFolder.toPath()).when(buildx).getDockerStateDir(Mockito.any(), Mockito.any());
        authConfigList = new AuthConfigList(new AuthConfig("testuser", "testpassword", null, null, null));
        givenAnImageConfiguration(new BuildXConfiguration.Builder()
            .dockerStateDir(temporaryFolder.getAbsolutePath())
            .platforms(Arrays.asList(FOREIGN1, FOREIGN2))
            .build());

        // When
        buildx.useBuilder(projectPaths, imageConfig, configuredRegistry, authConfigList, buildArchive, builder);

        // Then
        ArgumentCaptor<List<String>> buildXArgCaptor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(builder).useBuilder(buildXArgCaptor.capture(), anyString(), any(), eq(imageConfig), eq(configuredRegistry), eq(buildArchive));
        assertEquals(Arrays.asList("docker", "--config", temporaryFolder.getAbsolutePath(), "buildx"), buildXArgCaptor.getValue());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void useBuilder_whenDockerBuildXIncompatibleWithConfigOverride_thenCopyBuildXBinaryToTemporaryConfig(boolean isWindows) throws IOException, MojoExecutionException {
        try (MockedStatic<EnvUtil> envUtilMockedStatic = mockStatic(EnvUtil.class);
             MockedConstruction<BuildXService.BuildXListWithConfigCommand> ignore = mockConstruction(BuildXService.BuildXListWithConfigCommand.class, (mock, ctx) -> {
            when(mock.isSuccessFul()).thenReturn(false);
        })) {
            // Given
            Path configDirPath = temporaryFolder.toPath().resolve("docker-state-dir");
            Files.createDirectory(configDirPath);
            Files.createDirectory(temporaryFolder.toPath().resolve(".docker"));
            Files.createDirectory(temporaryFolder.toPath().resolve(".docker").resolve("cli-plugins"));
            final String buildxExecutableFilename = "docker-buildx" + (isWindows ? ".exe" : "");
            Files.createFile(temporaryFolder.toPath().resolve(".docker").resolve("cli-plugins").resolve(buildxExecutableFilename));
            envUtilMockedStatic.when(EnvUtil::getUserHome).thenReturn(temporaryFolder.getAbsolutePath());
            envUtilMockedStatic.when(EnvUtil::isWindows).thenReturn(isWindows);
            BuildXService.Builder<File> builder = Mockito.mock(BuildXService.Builder.class);

            givenAnImageConfiguration("linux/arm46", "linux/amd64");

            // When
            buildx.useBuilder(projectPaths, imageConfig, configuredRegistry, authConfigList, buildArchive, builder);

            // Then
            assertTrue(configDirPath.resolve("cli-plugins").toFile().exists());
            assertTrue(configDirPath.resolve("cli-plugins").resolve(buildxExecutableFilename).toFile().exists());
            verify(logger).debug("Detected current version of BuildX not working with --config override");
            verify(logger).debug("Copying BuildX binary to " + temporaryFolder.toPath().resolve("docker-state-dir"));
        }
    }


    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testBuildWithTag(boolean skipTag) throws Exception {
        List<String> tags = new ArrayList<>();
        tags.add("tag-" + System.currentTimeMillis());
        tags.add("tag-" + System.currentTimeMillis());

        //Given
        buildConfigUsingBuildx(temporaryFolder, (buildX, buildImage) -> {
            buildImage.skipTag(skipTag);
            buildImage.tags(tags);
        });

        // When
        buildx.build(projectPaths, imageConfig, configuredRegistry, authConfigList, buildArchive);

        String[] fullTags = tags.stream()
                .map(tag -> new ImageName(imageConfig.getName(), tag).getFullName(configuredRegistry))
                .toArray(String[]::new);
        if (skipTag) {
            verifyBuildXArgumentNotPresentInExec(fullTags);
        } else {
            verifyBuildXArgumentPresentInExec(fullTags);
        }
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

    private void verifyBuildXArgumentPresentInExec(String... args) throws Exception{
        ArgumentCaptor<List<String>> buildXArgCaptor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(exec).process(buildXArgCaptor.capture());
        for (String arg: args) {
            assertTrue(buildXArgCaptor.getValue().stream().anyMatch(passedArgument -> passedArgument.equalsIgnoreCase(arg)));
        }
    }

    private void verifyBuildXArgumentNotPresentInExec(String... args) throws Exception{
        ArgumentCaptor<List<String>> buildXArgCaptor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(exec).process(buildXArgCaptor.capture());
        for (String arg: args) {
            assertTrue(buildXArgCaptor.getValue().stream().noneMatch(passedArgument ->
                    passedArgument.toLowerCase().contains(arg.toLowerCase())));
        }
    }
}
