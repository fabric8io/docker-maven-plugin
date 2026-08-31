package io.fabric8.maven.docker.service;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.AuthConfigList;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.BuildXConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import io.fabric8.maven.docker.util.ProjectPaths;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistryServicePushImagesBuildXTest {
  private RegistryService registryService;
  private BuildXService buildXService;
  private List<ImageConfiguration> imageConfigurationList;
  private ProjectPaths projectPaths;
  private RegistryService.RegistryConfig registryConfig;
  private AuthConfigFactory authConfigFactory;
  private BuildService.BuildContext buildContext;
  private MojoParameters mojoParameters;
  private MavenProject mavenProject;
  private DockerAccess dockerAccess;

  @TempDir
  private File temporaryFolder;

  @BeforeEach
  void setUp() {
    buildXService = mock(BuildXService.class);
    Logger logger = mock(Logger.class);
    buildContext = mock(BuildService.BuildContext.class);
    authConfigFactory = mock(AuthConfigFactory.class);
    mojoParameters = mock(MojoParameters.class);
    mavenProject = mock(MavenProject.class);
    dockerAccess = mock(DockerAccess.class);
    QueryService queryService = new QueryService(dockerAccess);
    imageConfigurationList = Collections.singletonList(createNewImageConfiguration("user1/sample-image:latest", "foo/base:latest", null));
    registryConfig = new RegistryService.RegistryConfig.Builder()
        .registry("registry1.org")
        .authConfigFactory(authConfigFactory)
        .build();
    when(buildContext.getBuildArgs()).thenReturn(Collections.emptyMap());
    when(buildContext.getMojoParameters()).thenReturn(mojoParameters);
    when(mojoParameters.getProject()).thenReturn(mavenProject);
    when(mavenProject.getProperties()).thenReturn(new Properties());
    projectPaths = new ProjectPaths(temporaryFolder, "target/docker");
    registryService = new RegistryService(dockerAccess, queryService, buildXService, logger);
  }

  @Test
  void whenNoRegistryConfigured_thenAuthConfigEmpty() throws MojoExecutionException, DockerAccessException {
    // When
    registryService.pushImages(projectPaths, imageConfigurationList, 0, registryConfig, false, buildContext);

    // Then
    verifyBuildXServiceInvokedWithAuthConfigListSize(0);
  }

  @Test
  void whenOnlyPushRegistryConfigured_thenAuthConfigHasSingleEntry() throws MojoExecutionException, DockerAccessException {
    // Given
    givenAuthConfigExistsForRegistry("registry1.org", "user1", "password1");

    // When
    registryService.pushImages(projectPaths, imageConfigurationList, 0, registryConfig, false, buildContext);

    // Then
    verifyBuildXServiceInvokedWithAuthConfigListSize(1);
  }

  @Test
  void whenFromRegistryAndPushRegistryProvided_thenAuthConfigListContainsEntriesForBothPullAndPush() throws MojoExecutionException, DockerAccessException {
    // Given
    imageConfigurationList = Collections.singletonList(createNewImageConfiguration("user1/base-image-different-registry:latest", "registry2.org/user2/base:latest", null));
    givenAuthConfigExistsForRegistry("registry1.org", "user1", "password1");
    givenAuthConfigExistsForRegistry("registry2.org", "user2", "password2");

    // When
    registryService.pushImages(projectPaths, imageConfigurationList, 0, registryConfig, false, buildContext);

    // Then
    verifyBuildXServiceInvokedWithAuthConfigListSize(2);
  }

  @Test
  void whenDockerfileContainsFromReferencingMultipleRegistries_thenAuthConfigListContainsMultipleEntries() throws MojoExecutionException, IOException {
    // Given
    File dockerFile = new File(temporaryFolder, "Dockerfile");
    Files.write(dockerFile.toPath(), String.format("FROM registry2.org/user2/test-base:latest%n").getBytes());
    Files.write(dockerFile.toPath(), "FROM registry3.org/user3/scratch:1.0 AS build2".getBytes(), StandardOpenOption.APPEND);
    imageConfigurationList = Collections.singletonList(createNewImageConfiguration("user1/base-image-different-registry:latest", null, dockerFile));
    givenAuthConfigExistsForRegistry("registry1.org", "user1", "password1");
    givenAuthConfigExistsForRegistry("registry2.org", "user2", "password2");
    givenAuthConfigExistsForRegistry("registry3.org", "user3", "password2");

    // When
    registryService.pushImages(projectPaths, imageConfigurationList, 0, registryConfig, false, buildContext);

    // Then
    verifyBuildXServiceInvokedWithAuthConfigListSize(3);
  }

  @Test
  void whenBaseImageFromOtherRegistry_thenPushRegistryOfSubsequentImagesIsNotOverridden() throws MojoExecutionException, DockerAccessException {
    // Given two images, the first one having a base image hosted on another registry than the push registry
    imageConfigurationList = Arrays.asList(
        createNewImageConfiguration("user1/first-image:latest", "registry2.org/user2/base:latest", null),
        createNewImageConfiguration("user1/second-image:latest", "busybox:latest", null));
    givenAuthConfigExistsForRegistry("registry1.org", "user1", "password1");
    givenAuthConfigExistsForRegistry("registry2.org", "user2", "password2");

    // When
    registryService.pushImages(projectPaths, imageConfigurationList, 0, registryConfig, false, buildContext);

    // Then both images must be pushed to the configured push registry, not to the base image registry
    ArgumentCaptor<String> registryCaptor = ArgumentCaptor.forClass(String.class);
    verify(buildXService, times(2)).push(any(), any(), registryCaptor.capture(), any(), any());
    assertEquals(Arrays.asList("registry1.org", "registry1.org"), registryCaptor.getAllValues());
  }

  @Test
  void whenBaseImageFromOtherRegistry_thenLegacyPushOfSubsequentImageUsesPushRegistry() throws MojoExecutionException, DockerAccessException {
    // Given a buildx image whose base image comes from docker.io, followed by a plain (non-buildx) image
    imageConfigurationList = Arrays.asList(
        createNewImageConfiguration("user1/first-image:latest", "docker.io/library/openjdk:21", null),
        createNewLegacyImageConfiguration("user1/second-image:latest", "busybox:latest"));
    givenAuthConfigExistsForRegistry("registry1.org", "user1", "password1");
    givenAuthConfigExistsForRegistry("docker.io", "user2", "password2");

    // When
    registryService.pushImages(projectPaths, imageConfigurationList, 0, registryConfig, false, buildContext);

    // Then
    verify(dockerAccess).pushImage(eq("user1/second-image:latest"), any(), eq("registry1.org"), anyInt());
  }

  /**
   * Guards the invariant at its source, independently of which callers happen to reach the helper:
   * collecting pull credentials must leave the caller's registry configuration alone, since that
   * configuration is shared across every image of a `docker:push`.
   */
  @Test
  void whenAuthConfigListCreatedForBaseImages_thenRegistryConfigIsNotMutated() throws MojoExecutionException {
    // Given
    BuildImageConfiguration buildConfig = createNewImageConfiguration("user1/sample-image:latest", "registry2.org/user2/base:latest", null)
        .getBuildConfiguration();
    givenAuthConfigExistsForRegistry("registry2.org", "user2", "password2");

    // When
    AuthConfigList authConfigList = RegistryService.createAuthConfigListForBaseImages(
        buildConfig, mojoParameters, "registry1.org", registryConfig, Collections.emptyMap());

    // Then
    assertEquals(1, authConfigList.size());
    assertEquals("registry1.org", registryConfig.getRegistry());
  }

  private void verifyBuildXServiceInvokedWithAuthConfigListSize(int expectedSize) throws MojoExecutionException {
    ArgumentCaptor<AuthConfigList> authConfigListArgumentCaptor = ArgumentCaptor.forClass(AuthConfigList.class);
    verify(buildXService).push(any(), any(), anyString(), authConfigListArgumentCaptor.capture(), any());
    assertEquals(expectedSize, authConfigListArgumentCaptor.getValue().size());
  }

  private void givenAuthConfigExistsForRegistry(String registry, String username, String password) throws MojoExecutionException {
    AuthConfig auth = new AuthConfig(username, password, null, null);
    auth.setRegistry(registry);

    when(authConfigFactory.createAuthConfig(anyBoolean(), anyBoolean(), any(), any(), any(), eq(registry)))
        .thenReturn(auth);
  }

  private ImageConfiguration createNewLegacyImageConfiguration(String name, String from) {
    BuildImageConfiguration buildImageConfiguration = mock(BuildImageConfiguration.class);
    when(buildImageConfiguration.getFrom()).thenReturn(from);
    when(buildImageConfiguration.isBuildX()).thenReturn(false);
    when(buildImageConfiguration.getTags()).thenReturn(Collections.emptyList());
    return new ImageConfiguration.Builder()
        .name(name)
        .buildConfig(buildImageConfiguration).build();
  }

  private ImageConfiguration createNewImageConfiguration(String name, String from, File dockerFile) {
    BuildImageConfiguration buildImageConfiguration = mock(BuildImageConfiguration.class);
    when(buildImageConfiguration.getFrom()).thenReturn(from);
    when(buildImageConfiguration.getBuildX()).thenReturn(new BuildXConfiguration.Builder()
        .platforms(Arrays.asList("linux/amd64", "linux/arm64"))
        .build());
    when(buildImageConfiguration.isBuildX()).thenReturn(true);
    when(buildImageConfiguration.getDockerFile()).thenReturn(dockerFile);
    when(buildImageConfiguration.getAbsoluteDockerFilePath(any())).thenReturn(dockerFile);
    return new ImageConfiguration.Builder()
        .name(name)
        .buildConfig(buildImageConfiguration).build();
  }
}
