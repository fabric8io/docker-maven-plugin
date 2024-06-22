package io.fabric8.maven.docker.service.helper;

import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuildArgResolverTest {
  private Properties projectProperties;
  private BuildArgResolver buildArgResolver;
  private BuildService.BuildContext buildContext;

  @BeforeEach
  void setUp() {
    projectProperties = new Properties();
    Logger log = mock(Logger.class);
    MojoParameters mojoParameters = mock(MojoParameters.class);
    MavenProject mavenProject = mock(MavenProject.class);
    buildContext = new BuildService.BuildContext.Builder()
      .mojoParameters(mojoParameters)
      .build();
    when(mavenProject.getProperties()).thenReturn(projectProperties);
    when(mojoParameters.getProject()).thenReturn(mavenProject);
    buildArgResolver = new BuildArgResolver(log);
  }

  @Test
  @DisplayName("build args in project properties")
  void whenBuildArgsFromProjectProperties_shouldMergeBuildArgs() {
    // Given
    projectProperties.setProperty("docker.buildArg.VERSION", "latest");
    projectProperties.setProperty("docker.buildArg.FULL_IMAGE", "busybox:latest");

    // When
    Map<String, String> mergedBuildArgs = buildArgResolver.resolveBuildArgs(buildContext);

    // Then
    assertThat(mergedBuildArgs)
      .containsEntry("VERSION", "latest")
      .containsEntry("FULL_IMAGE", "busybox:latest");
  }

  @Test
  @DisplayName("build args in project properties, system properties")
  void fromAllSourcesWithDifferentKeys_shouldMergeBuildArgs() {
    // Given
    System.setProperty("docker.buildArg.IMAGE-1", "openjdk");
    projectProperties.setProperty("docker.buildArg.REPO_1", "docker.io/library");

    // When
    Map<String, String> mergedBuildArgs = buildArgResolver.resolveBuildArgs(buildContext);

    // Then
    assertThat(mergedBuildArgs)
      .containsEntry("REPO_1", "docker.io/library")
      .containsEntry("IMAGE-1", "openjdk");
  }

  @Nested
  @DisplayName("local ~/.docker/config.json contains proxy settings")
  class LocalDockerConfigContainsProxySettings {
    @BeforeEach
    void setUp() throws IOException {
      Path dockerConfig = Files.createTempDirectory("docker-config");
      dockerConfig.toFile().deleteOnExit();
      final Map<String, String> env = Collections.singletonMap("DOCKER_CONFIG", dockerConfig.toFile().getAbsolutePath());
      EnvUtil.overrideEnvGetter(env::get);
      Files.write(dockerConfig.resolve("config.json"), (String.format("{\"proxies\": {\"default\": {%n" +
        "     \"httpProxy\": \"http://proxy.example.com:3128\",%n" +
        "     \"httpsProxy\": \"https://proxy.example.com:3129\",%n" +
        "     \"noProxy\": \"*.test.example.com,.example.org,127.0.0.0/8\"%n" +
        "   }}}")).getBytes());
    }

    @Test
    @DisplayName("mergeBuildArgsIncludingLocalDockerConfigProxySettings, should add proxy build args for docker build strategy")
    void shouldAddBuildArgsFromDockerConfigInDockerBuild() {
      // When
      final Map<String, String> mergedBuildArgs = buildArgResolver.resolveBuildArgs(buildContext);
      // Then
      assertThat(mergedBuildArgs)
        .containsEntry("http_proxy", "http://proxy.example.com:3128")
        .containsEntry("https_proxy", "https://proxy.example.com:3129")
        .containsEntry("no_proxy", "*.test.example.com,.example.org,127.0.0.0/8");
    }

    @AfterEach
    void tearDown() {
      EnvUtil.overrideEnvGetter(System::getenv);
    }
  }

  @AfterEach
  void clearSystemPropertiesUsedInTests() {
    System.clearProperty("docker.buildArg.IMAGE-1");
    System.clearProperty("docker.buildArg.VERSION");
  }
}
