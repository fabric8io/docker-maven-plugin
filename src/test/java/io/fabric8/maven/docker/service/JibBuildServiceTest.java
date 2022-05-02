package io.fabric8.maven.docker.service;

import com.google.cloud.tools.jib.api.Credential;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import io.fabric8.maven.docker.util.JibServiceUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class JibBuildServiceTest {

    @Mock
    private Logger logger;

    @Mock
    private ServiceHub serviceHub;

    @Mock
    private Settings settings;

    @Mock
    private MojoParameters params;

    @Mock
    private MavenProject project;

    @Mock
    private AuthConfigFactory authConfigFactory;

    @Mock
    private DockerAssemblyManager dockerAssemblyManager;

    @Test
    @SuppressWarnings("squid:S00112")
    void testGetRegistryCredentialsForPush() throws MojoExecutionException {
        // Given
        ImageConfiguration imageConfiguration = getImageConfiguration();
        RegistryService.RegistryConfig registryConfig = new RegistryService.RegistryConfig.Builder()
                .authConfig(Collections.emptyMap())
                .authConfigFactory(authConfigFactory)
                .settings(settings)
                .build();
        mockAuthConfigFactory(true, registryConfig);
        // When
        Credential credential = JibBuildService.getRegistryCredentials(
                registryConfig, true, imageConfiguration, logger);
        // Then
        Assertions.assertNotNull(credential);
        Assertions.assertEquals("testuserpush", credential.getUsername());
        Assertions.assertEquals("testpass", credential.getPassword());
    }

    @Test
    @SuppressWarnings("squid:S00112")
    void testGetRegistryCredentialsForPull() throws MojoExecutionException {
        // Given
        ImageConfiguration imageConfiguration = getImageConfiguration();
        RegistryService.RegistryConfig registryConfig = new RegistryService.RegistryConfig.Builder()
                .authConfig(Collections.emptyMap())
                .authConfigFactory(authConfigFactory)
                .settings(settings)
                .build();
        mockAuthConfigFactory(false, registryConfig);
        // When
        Credential credential = JibBuildService.getRegistryCredentials(
                registryConfig, false, imageConfiguration, logger);
        // Then
        Assertions.assertNotNull(credential);
        Assertions.assertEquals("testuserpull", credential.getUsername());
        Assertions.assertEquals("testpass", credential.getPassword());
    }

    @Test
    void testGetBuildTarArchive() throws IOException {
        // Given
        File projectBaseDir = Files.createTempDirectory("test").toFile();
        ImageConfiguration imageConfiguration = getImageConfiguration();
        setupServiceHubExpectations(projectBaseDir);

        // When
        File tarArchive = JibBuildService.getBuildTarArchive(imageConfiguration, params);

        // Then
        Assertions.assertNotNull(tarArchive);
        Assertions.assertEquals(new File("/target/test/testimage/0.0.1/tmp/docker-build.tar").getPath(),
                tarArchive.getAbsolutePath().substring(projectBaseDir.getAbsolutePath().length()));
    }


    @Test
    void testGetAssemblyTarArchive() throws IOException, MojoExecutionException {
        // Given
        Path projectBaseDir = Files.createTempDirectory("test");
        ImageConfiguration imageConfiguration = getImageConfiguration();
        setupDockerAssemblyExpectations(projectBaseDir);

        // When
        File tarArchive = JibBuildService.getAssemblyTarArchive(imageConfiguration, serviceHub, params, logger);

        // Then
        Assertions.assertNotNull(tarArchive);
        Assertions.assertEquals(new File("/target/test/testimage/0.0.1/tmp/docker-build.tar").getPath(),
                tarArchive.getAbsolutePath().substring(projectBaseDir.toString().length()));
    }

    @Test
    void testPrependRegistry() {
        // Given
        ImageConfiguration imageConfiguration = getImageConfiguration();
        // When
        JibBuildService.prependRegistry(imageConfiguration, "quay.io");
        // Then
        Assertions.assertNotNull(imageConfiguration);
        Assertions.assertEquals("quay.io/test/testimage:0.0.1", imageConfiguration.getName());
    }

    @Test
    void testPushWithNoConfigurations() {
        try (MockedStatic<JibServiceUtil> jibServiceUtilMock = Mockito.mockStatic(JibServiceUtil.class)) {
            // Given
            jibServiceUtilMock
                .when(() -> JibServiceUtil.jibPush(Mockito.any(ImageConfiguration.class), Mockito.any(Credential.class), Mockito.any(File.class), Mockito.any(Logger.class)))
                .thenThrow(new AssertionError("JibPush was invoked"));
            // When
            JibBuildService jibBuildService = new JibBuildService(serviceHub, params, logger);
            List<ImageConfiguration> emptyList = Collections.emptyList();
            // Then
            Assertions.assertDoesNotThrow(() -> jibBuildService.push(emptyList, 1, null, false));
        }
    }

    @Test
    @Disabled("Cannot intercept JibServiceUtil.pushImage() to prevent actual image creation")
    void testPushWithConfiguration(@TempDir Path tmpDir) throws Exception {
        try (MockedStatic<JibServiceUtil> jibServiceUtilMock = Mockito.mockStatic(JibServiceUtil.class)) {
            // Given
            setupServiceHubExpectations(tmpDir.toFile());
            setupDockerAssemblyExpectations(tmpDir);
            final ImageConfiguration imageConfiguration = getImageConfiguration();
            final RegistryService.RegistryConfig registryConfig = new RegistryService.RegistryConfig.Builder()
                .authConfigFactory(authConfigFactory)
                .build();
            mockAuthConfigFactory(true, registryConfig);

            // When
            new JibBuildService(serviceHub, params, logger).push(Collections.singletonList(imageConfiguration), 1, registryConfig, false);

            jibServiceUtilMock.verify(() -> JibServiceUtil.jibPush(
                Mockito.eq(imageConfiguration),
                Mockito.eq(Credential.from("testuserpush", "testpass")),
                Mockito.any(File.class),
                Mockito.eq(logger)));
        }
    }

    private ImageConfiguration getImageConfiguration() {
        return new ImageConfiguration.Builder()
                .name("test/testimage:0.0.1")
                .buildConfig(new BuildImageConfiguration.Builder().from("busybox").build())
                .build();
    }

    @SuppressWarnings("squid:S00112")
    private void setupServiceHubExpectations(File projectBaseDir) {
        Mockito.doReturn(projectBaseDir).when(project).getBasedir();
        Mockito.doReturn("target").when(params).getOutputDirectory();
        Mockito.doReturn(project).when(params).getProject();
    }

    private void setupDockerAssemblyExpectations(Path projectBaseDir) throws MojoExecutionException, IOException {
        Path dockerTar = projectBaseDir.resolve("target/test/testimage/0.0.1/tmp/docker-build.tar");
        Files.createDirectories(dockerTar.getParent());
        Files.createFile(dockerTar);
        Mockito.doReturn(dockerTar.toFile())
            .when(dockerAssemblyManager)
            .createDockerTarArchive(Mockito.anyString(), Mockito.eq(params), Mockito.any(BuildImageConfiguration.class), Mockito.eq(logger), Mockito.isNull());

        Mockito.doReturn(dockerAssemblyManager).when(serviceHub).getDockerAssemblyManager();
    }


    private void mockAuthConfigFactory(boolean isPush, RegistryService.RegistryConfig registryConfig) throws MojoExecutionException {
        Mockito.doReturn(new AuthConfig("testuser" + (isPush ? "push" : "pull"), "testpass", "foo@example.com", null, null))
            .when(authConfigFactory)
            .createAuthConfig(Mockito.anyBoolean(), Mockito.eq(registryConfig.isSkipExtendedAuth()), Mockito.eq(registryConfig.getAuthConfig()),
                Mockito.eq(registryConfig.getSettings()), Mockito.isNull(), Mockito.anyString());
    }
}
