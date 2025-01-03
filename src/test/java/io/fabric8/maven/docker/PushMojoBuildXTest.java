package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.BuildXConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.handler.ImageConfigResolver;
import io.fabric8.maven.docker.config.handler.compose.DockerComposeConfigHandler;
import io.fabric8.maven.docker.config.handler.property.PropertyConfigHandler;
import io.fabric8.maven.docker.service.BuildXService;
import io.fabric8.maven.docker.service.DockerAccessFactory;
import io.fabric8.maven.docker.service.ServiceHubFactory;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.DefaultSettingsDecrypter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushMojoBuildXTest {
  private PushMojo pushMojo;
  @TempDir
  private File temporaryFolder;

  private Path expectedDockerStateDir;
  private File expectedDockerStateConfigDir;
  private Settings mockedMavenSettings;
  private MockedConstruction<BuildXService.DefaultExec> defaultExecMockedConstruction;

  @BeforeEach
  void setup() throws MojoExecutionException, MojoFailureException, IOException, SecDispatcherException {
    mockedMavenSettings = mock(Settings.class);
    MavenProject mavenProject = mock(MavenProject.class, RETURNS_DEEP_STUBS);
    DockerAccessFactory dockerAccessFactory = mock(DockerAccessFactory.class);
    DockerAccess dockerAccess = mock(DockerAccess.class);
    SecDispatcher mockedSecDispatcher = mock(SecDispatcher.class);
    ServiceHubFactory serviceHubFactory = new ServiceHubFactory();
    when(mockedMavenSettings.getInteractiveMode()).thenReturn(false);
    Properties properties = new Properties();
    when(mavenProject.getProperties()).thenReturn(properties);
    File targetDir = temporaryFolder.toPath().resolve("target").toFile();
    expectedDockerStateDir = targetDir.toPath().resolve("docker")
        .resolve("test.example.org").resolve("testuser").resolve("sample-test-image")
        .resolve("latest");
    expectedDockerStateConfigDir = expectedDockerStateDir.resolve("docker").toFile();
    Files.createDirectory(targetDir.toPath());
    when(mavenProject.getBuild().getDirectory()).thenReturn(targetDir.getAbsolutePath());
    when(mavenProject.getBuild().getOutputDirectory()).thenReturn(targetDir.getAbsolutePath());
    when(mavenProject.getBasedir()).thenReturn(temporaryFolder);
    when(dockerAccessFactory.createDockerAccess(any())).thenReturn(dockerAccess);
    when(mockedSecDispatcher.decrypt(anyString())).thenReturn("testpassword");
    Map<String, Object> pluginContext = new HashMap<>();
    defaultExecMockedConstruction = mockConstruction(BuildXService.DefaultExec.class);
    this.pushMojo = new PushMojo();
    this.pushMojo.setPluginContext(pluginContext);
    pushMojo.verbose = "true";
    pushMojo.settings = mockedMavenSettings;
    pushMojo.project = mavenProject;
    pushMojo.authConfigFactory = new AuthConfigFactory(new DefaultSettingsDecrypter(mockedSecDispatcher));
    pushMojo.imageConfigResolver = new ImageConfigResolver(Arrays.asList(new PropertyConfigHandler(), new DockerComposeConfigHandler()));
    pushMojo.dockerAccessFactory = dockerAccessFactory;
    pushMojo.serviceHubFactory = serviceHubFactory;
    pushMojo.outputDirectory = "target/docker";
    pushMojo.images = Collections.singletonList(createImageConfiguration());
  }

  @AfterEach
  void tearDown() {
    defaultExecMockedConstruction.close();
  }

  @Test
  @DisplayName("docker:push buildx, auth from ~/.m2/settings.xml, then add --config option to buildx")
  void execute_whenAuthConfigFromMavenSettings_thenAddConfigToDockerBuildXCommand() throws MojoExecutionException, MojoFailureException {
    // Given
    Server server = new Server();
    server.setId("test.example.org");
    server.setUsername("testuser");
    server.setPassword("testpassword");
    when(mockedMavenSettings.getServers()).thenReturn(Collections.singletonList(server));

    // When
    pushMojo.execute();

    // Then
    verifyDockerConfigOptionAddedToBuildX();
  }

  @Test
  @DisplayName("docker:push buildx, auth from properties, then add --config option to buildx")
  void execute_whenAuthConfigFromProperties_thenAddConfigOptionToBuildXCommand() throws MojoExecutionException, MojoFailureException {
    try {
      // Given
      System.setProperty("docker.push.username", "testuser");
      System.setProperty("docker.push.password", "testpassword");

      // When
      pushMojo.execute();

      // Then
      verifyDockerConfigOptionAddedToBuildX();
    } finally {
      System.clearProperty("docker.push.username");
      System.clearProperty("docker.push.password");
    }
  }

  private void verifyNoDockerConfigAddedToBuildX() throws MojoExecutionException {
    assertEquals(1, defaultExecMockedConstruction.constructed().size());
    BuildXService.DefaultExec defaultExec = defaultExecMockedConstruction.constructed().get(0);
    verify(defaultExec).process(Arrays.asList("docker", "buildx", "create",
        "--driver", "docker-container", "--name", "testbuilder", "--node", "testnode"));
    verify(defaultExec).process(Arrays.asList("docker", "buildx", "build",
        "--progress=plain", "--builder", "testbuilder", "--platform", "linux/amd64,linux/arm64",
        "--tag", "test.example.org/testuser/sample-test-image:latest",
        expectedDockerStateDir.resolve("build").toFile().getAbsolutePath(), "--push"));
  }

  private void verifyDockerConfigOptionAddedToBuildX() throws MojoExecutionException {
    assertEquals(1, defaultExecMockedConstruction.constructed().size());
    BuildXService.DefaultExec defaultExec = defaultExecMockedConstruction.constructed().get(0);
    verify(defaultExec).process(Arrays.asList("docker", "--config", expectedDockerStateConfigDir.getAbsolutePath(), "buildx", "create",
        "--driver", "docker-container", "--name", "testbuilder", "--node", "testnode"));
    verify(defaultExec).process(Arrays.asList("docker", "--config", expectedDockerStateConfigDir.getAbsolutePath(), "buildx", "build",
        "--progress=plain", "--builder", "testbuilder", "--platform", "linux/amd64,linux/arm64",
        "--tag", "test.example.org/testuser/sample-test-image:latest",
        expectedDockerStateDir.resolve("build").toFile().getAbsolutePath(), "--push"));
  }

  private ImageConfiguration createImageConfiguration() {
    return new ImageConfiguration.Builder()
        .name("test.example.org/testuser/sample-test-image:latest")
        .buildConfig(new BuildImageConfiguration.Builder()
            .from("test.example.org/base/builder:latest")
            .buildx(new BuildXConfiguration.Builder()
                .platforms(Arrays.asList("linux/amd64", "linux/arm64"))
                .builderName("testbuilder")
                .nodeName("testnode")
                .build())
            .build())
        .build();
  }
}
