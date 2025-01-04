package io.fabric8.maven.docker.service;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.assembly.BuildDirs;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.BuildXConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.ProjectPaths;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BuildXServiceCreateBuilderTest {
  private BuildXService buildXService;
  private ImageConfiguration imageConfig;
  private BuildXService.Exec exec;
  private BuildDirs buildDirs;
  private Path configPath;
  private List<String> execArgs;

  @TempDir
  private File temporaryFolder;

  @BeforeEach
  void setUp() {
    configPath = temporaryFolder.toPath().resolve(".docker").resolve("config.json");
    buildDirs = new BuildDirs(new ProjectPaths(temporaryFolder, "target"), "foo/bar:latest");
    DockerAssemblyManager dockerAssemblyManager = mock(DockerAssemblyManager.class);
    DockerAccess dockerAccess = mock(DockerAccess.class);
    Logger logger = mock(Logger.class);
    exec = mock(BuildXService.Exec.class);
    buildXService = new BuildXService(dockerAccess, dockerAssemblyManager, logger, exec);
  }

  @Test
  void driverOptIsPresentIfProvided() throws Exception {
    //Given
    buildConfigUsingBuildX(temporaryFolder, (buildX, buildImage) -> buildX.driverOpts(Collections.singletonMap("network", "foonet")));

    // When
    buildXService.createBuilder(configPath, Arrays.asList("docker", "buildx"), imageConfig, buildDirs);

    //Then
    verifyBuildXArgumentContains("--driver-opt", "network=foonet");
  }



  @Test
  void builderPathWithLowerCasedBuilderName() throws Exception {
    String builderName =  "myTestBuilder";
    Path configPathSpy = Mockito.spy(configPath);
    Path expectedPath = Paths.get("buildx","instances",builderName.toLowerCase());

    //Given
    buildConfigUsingBuildX(temporaryFolder,(buildX, buildImage) -> buildX.builderName(builderName));

    // When
    buildXService.createBuilder(configPathSpy, Arrays.asList("docker", "buildx"), imageConfig, buildDirs);

    // Then
    verify(configPathSpy).resolve(eq(expectedPath));
  }

  @Test
  void driverOptIsAbsentIfNotProvided() throws Exception {
    //Given
    buildConfigUsingBuildX(temporaryFolder, (buildX, buildImage) -> {});

    // When
    buildXService.createBuilder(configPath, Arrays.asList("docker", "buildx"), imageConfig, buildDirs);

    //Then
    verifyBuildXArgumentDoesNotContain("--driver-opt", "network=foonet");
  }

  private void captureBuildXArguments() throws MojoExecutionException {
    ArgumentCaptor<List<String>> buildXArgCaptor = ArgumentCaptor.forClass(List.class);
    Mockito.verify(exec).process(buildXArgCaptor.capture());
    execArgs = buildXArgCaptor.getValue();
  }

  private void verifyBuildXArgumentContains(String... args) throws MojoExecutionException {
    captureBuildXArguments();
    assertTrue(execArgs.containsAll(Arrays.asList(args)));
  }

  private void verifyBuildXArgumentDoesNotContain(String... args) throws MojoExecutionException {
    captureBuildXArguments();
    assertFalse(execArgs.containsAll(Arrays.asList(args)));
  }

  private void buildConfigUsingBuildX(File temporaryFolder, BiConsumer<BuildXConfiguration.Builder, BuildImageConfiguration.Builder> spec) {
    final BuildXConfiguration.Builder buildXConfigurationBuilder = new BuildXConfiguration.Builder()
        .dockerStateDir(temporaryFolder.getAbsolutePath())
        .platforms(Collections.singletonList("linux/amd64"));
    final BuildImageConfiguration.Builder buildImageConfigBuilder = new BuildImageConfiguration.Builder();
    spec.accept(buildXConfigurationBuilder, buildImageConfigBuilder);
    final BuildXConfiguration buildXConfig = buildXConfigurationBuilder.build();
    final BuildImageConfiguration buildImageConfig = buildImageConfigBuilder
        .buildx(buildXConfig)
        .build();
    imageConfig = new ImageConfiguration.Builder()
        .name("foo/bar:latest")
        .buildConfig(buildImageConfig)
        .build();
  }
}
