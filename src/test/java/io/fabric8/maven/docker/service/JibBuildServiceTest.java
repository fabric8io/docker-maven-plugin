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
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class JibBuildServiceTest {

    @Mocked
    private Logger logger;

    @Mocked
    private ServiceHub serviceHub;

    @Mocked
    private Settings settings;

    @Mocked
    private MojoParameters params;

    @Mocked
    private MavenProject project;

    @Mocked
    private AuthConfigFactory authConfigFactory;

    @Mocked
    private DockerAssemblyManager dockerAssemblyManager;

    @Test
    @java.lang.SuppressWarnings("squid:S00112")
    public void testGetRegistryCredentialsForPush() throws MojoExecutionException {
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
        assertNotNull(credential);
        assertEquals("testuserpush", credential.getUsername());
        assertEquals("testpass", credential.getPassword());
    }

    @Test
    @java.lang.SuppressWarnings("squid:S00112")
    public void testGetRegistryCredentialsForPull() throws MojoExecutionException {
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
        assertNotNull(credential);
        assertEquals("testuserpull", credential.getUsername());
        assertEquals("testpass", credential.getPassword());
    }

    @Test
    public void testGetBuildTarArchive() throws IOException {
        // Given
        File projectBaseDir = Files.createTempDirectory("test").toFile();
        ImageConfiguration imageConfiguration = getImageConfiguration();
        setupServiceHubExpectations(projectBaseDir);

        // When
        File tarArchive = JibBuildService.getBuildTarArchive(imageConfiguration, params);

        // Then
        assertNotNull(tarArchive);
        assertEquals(new File("/target/test/testimage/0.0.1/tmp/docker-build.tar").getPath(),
                tarArchive.getAbsolutePath().substring(projectBaseDir.getAbsolutePath().length()));
    }


    @Test
    public void testGetAssemblyTarArchive() throws IOException, MojoExecutionException {
        // Given
        File projectBaseDir = Files.createTempDirectory("test").toFile();
        ImageConfiguration imageConfiguration = getImageConfiguration();
        setupDockerAssemblyExpectations(projectBaseDir);

        // When
        File tarArchive = JibBuildService.getAssemblyTarArchive(imageConfiguration, serviceHub, params, logger);

        // Then
        assertNotNull(tarArchive);
        assertEquals(new File("/target/test/testimage/0.0.1/tmp/docker-build.tar").getPath(),
                tarArchive.getAbsolutePath().substring(projectBaseDir.getAbsolutePath().length()));
    }

    @Test
    public void testPrependRegistry() {
        // Given
        ImageConfiguration imageConfiguration = getImageConfiguration();
        // When
        JibBuildService.prependRegistry(imageConfiguration, "quay.io");
        // Then
        assertNotNull(imageConfiguration);
        assertEquals("quay.io/test/testimage:0.0.1", imageConfiguration.getName());
    }

    @Test
    public void testPushWithNoConfigurations(@Mocked JibServiceUtil jibServiceUtil) throws Exception {
        // When
        new JibBuildService(serviceHub, params, logger).push(Collections.emptyList(), 1, null, false);
        // Then
        // @formatter:off
        new Verifications() {{
            JibServiceUtil.jibPush((ImageConfiguration) any, (Credential) any, (File) any, logger); times = 0;
        }};
        // @formatter:on
    }

    @Test
    public void testPushWithConfiguration(@Mocked JibServiceUtil jibServiceUtil) throws Exception {
        // Given
        File projectBaseDir = Files.createTempDirectory("test").toFile();
        setupServiceHubExpectations(projectBaseDir);
        final ImageConfiguration imageConfiguration = getImageConfiguration();
        final RegistryService.RegistryConfig registryConfig = new RegistryService.RegistryConfig.Builder()
                .authConfigFactory(authConfigFactory)
                .build();
        mockAuthConfigFactory(true, registryConfig);
        // When
        new JibBuildService(serviceHub, params, logger).push(Collections.singletonList(imageConfiguration), 1, registryConfig, false);
        // Then
        // @formatter:off
        new Verifications() {{
            JibServiceUtil.jibPush(
                    imageConfiguration,
                    Credential.from("testuserpush", "testpass"),
                    (File)any,
                    logger);
            times = 1;
        }};
        // @formatter:on
    }

    private ImageConfiguration getImageConfiguration() {
        return new ImageConfiguration.Builder()
                .name("test/testimage:0.0.1")
                .buildConfig(new BuildImageConfiguration.Builder().from("busybox").build())
                .build();
    }

    @java.lang.SuppressWarnings("squid:S00112")
    private void setupServiceHubExpectations(File projectBaseDir) {
        new Expectations() {{
            project.getBasedir();
            result = projectBaseDir;

            params.getOutputDirectory();
            result = "target";

            params.getProject();
            result = project;
        }};
    }

    private void setupDockerAssemblyExpectations(File projectBaseDir) throws MojoExecutionException {
        new Expectations() {{
            dockerAssemblyManager.createDockerTarArchive(anyString, params, (BuildImageConfiguration) any, logger, null);
            result = new File(projectBaseDir, "target/test/testimage/0.0.1/tmp/docker-build.tar");

            serviceHub.getDockerAssemblyManager();
            result = dockerAssemblyManager;
        }};
    }


    private void mockAuthConfigFactory(boolean isPush, RegistryService.RegistryConfig registryConfig) throws MojoExecutionException {
        new Expectations() {{
            authConfigFactory.createAuthConfig(anyBoolean, registryConfig.isSkipExtendedAuth(), registryConfig.getAuthConfig(), registryConfig.getSettings(), null, anyString);
            result = new AuthConfig("testuser" + (isPush ? "push" : "pull"), "testpass", "foo@example.com", null, null);
        }};
    }
}
