package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.AuthConfigList;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.AttestationConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.BuildXConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.service.BuildXService;
import io.fabric8.maven.docker.service.ImagePullManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class BuildMojoTest extends MojoTestBase {
    private static final String NON_NATIVE_PLATFORM = "linux/amd64";
    private static final String NATIVE_PLATFORM = "linux/arm64";
    private static final String[] TWO_BUILDX_PLATFORMS = { NATIVE_PLATFORM, NON_NATIVE_PLATFORM };

    private static final String TEST_REGISTRY = "test-registry.org";

    @InjectMocks
    private BuildMojo buildMojo;

    @Mock
    private DockerAssemblyManager dockerAssemblyManager;

    @TempDir
    private Path tmpDir;

    @Mock
    private BuildXService.Exec exec;
    private MockedConstruction<BuildXService.DockerVersionExternalCommand> dockerVersionExternalCommandMockedConstruction;

    private static String getOsDependentBuild(Path buildPath, String docker) {
        return buildPath.resolve(docker).toString().replace('/', File.separatorChar);
    }

    @BeforeEach
    void setUp() {
        dockerVersionExternalCommandMockedConstruction = Mockito.mockConstruction(BuildXService.DockerVersionExternalCommand.class);
    }

    @AfterEach
    void tearDown() {
        dockerVersionExternalCommandMockedConstruction.close();
    }

    @Test
    void skipWhenPom() throws IOException, MojoExecutionException {

        givenMavenProject(buildMojo);
        givenPackaging("pom");
        givenSkipPom(true);

        whenMojoExecutes();

        thenBuildNotRun();
    }

    @Test
    void noSkipWhenNotPom() throws IOException, MojoExecutionException {
        givenMavenProject(buildMojo);
        givenResolvedImages(buildMojo, Collections.singletonList(singleImageWithBuild()));
        givenPackaging("jar");
        givenSkipPom(true);

        whenMojoExecutes();

        thenBuildRun();
    }

    @Test
    void buildUsingBuildx() throws IOException, MojoExecutionException {
        givenBuildXService();

        givenMavenProject(buildMojo);
        givenResolvedImages(buildMojo, Collections.singletonList(singleBuildXImageWithContext(null)));
        givenPackaging("jar");

        whenMojoExecutes();

        thenBuildxRun(null, null, true, null );
    }

    @Test
    void buildUsingConfiguredBuildx() throws IOException, MojoExecutionException {
        givenBuildXService();

        givenMavenProject(buildMojo);
        givenResolvedImages(buildMojo, Collections.singletonList(singleBuildXImageWithConfiguration("src/docker/builder.toml")));
        givenPackaging("jar");

        whenMojoExecutes();

        thenBuildxRun("src/docker/builder.toml", null, true, null );
    }

    @Test
    void buildUsingConfiguredBuildxWithContext() throws IOException, MojoExecutionException {
        givenBuildXService();

        givenMavenProject(buildMojo);
        ImageConfiguration imageConfiguration = singleBuildXImageWithContext("src/main/docker");
        givenResolvedImages(buildMojo, Collections.singletonList(imageConfiguration));
        givenPackaging("jar");

        Mockito.doReturn(tmpDir.resolve("docker-build.tar").toFile())
            .when(buildService)
            .buildArchive(Mockito.any(), Mockito.any(), Mockito.any());

        whenMojoExecutes();

        thenBuildxRun(null, "src/main/docker", true,null );
    }

    @Test
    void buildUsingBuildxWithNonNative() throws IOException, MojoExecutionException {
        givenBuildXService();

        givenMavenProject(buildMojo);
        ImageConfiguration imageConfiguration = singleBuildXImageNonNative();
        givenResolvedImages(buildMojo, Collections.singletonList(imageConfiguration));
        givenPackaging("jar");

        Mockito.doReturn(tmpDir.resolve("docker-build.tar").toFile())
            .when(buildService)
            .buildArchive(Mockito.any(), Mockito.any(), Mockito.any());

        whenMojoExecutes();
        thenBuildxRun(null, null, false,null );
    }

    @Test
    void buildUsingBuildxWithSquash() throws IOException, MojoExecutionException {
        givenBuildXService();

        givenMavenProject(buildMojo);
        givenResolvedImages(buildMojo, Collections.singletonList(singleBuildXImageWithSquash()));
        givenPackaging("jar");

        whenMojoExecutes();

        thenBuildxRun(null, null, true, "--squash");
    }

    @Test
    void buildUsingBuildxWithSbom() throws IOException, MojoExecutionException {
        givenBuildXService();

        givenMavenProject(buildMojo);
        givenResolvedImages(buildMojo, Collections.singletonList(singleBuildXImageWithAttestations(true, null)));
        givenPackaging("jar");

        whenMojoExecutes();

        thenBuildxRun(null, null, true, "--sbom=true");
    }

    @Test
    void buildUsingBuildxWithMaxProvenance() throws IOException, MojoExecutionException {
        givenBuildXService();

        givenMavenProject(buildMojo);
        givenResolvedImages(buildMojo, Collections.singletonList(singleBuildXImageWithAttestations(false, "max")));
        givenPackaging("jar");

        whenMojoExecutes();

        thenBuildxRun(null, null, true, "--provenance=mode=max");
    }

    @Test
    void buildUsingBuildxWithNoProvenance() throws IOException, MojoExecutionException {
        givenBuildXService();

        givenMavenProject(buildMojo);
        givenResolvedImages(buildMojo, Collections.singletonList(singleBuildXImageWithAttestations(false, "false")));
        givenPackaging("jar");

        whenMojoExecutes();

        thenBuildxRun(null, null, true, "--provenance=false");
    }

    @Test
    void buildUsingBuildxWithIncorrectProvenanceMode() throws IOException, MojoExecutionException {
        givenBuildXService();

        givenMavenProject(buildMojo);
        givenResolvedImages(buildMojo, Collections.singletonList(singleBuildXImageWithAttestations(false, "garbage")));
        givenPackaging("jar");

        whenMojoExecutes();

        thenBuildxRun(null, null, true, null);
    }

    @Test
    void buildUsingBuildxWithAuth() throws IOException, MojoExecutionException {
        Mockito.doReturn(Mockito.mock(BuildXService.class)).when(serviceHub).getBuildXService();

        givenMavenProject(buildMojo);
        givenResolvedImages(buildMojo, Collections.singletonList(singleBuildXImageWithContext(null)));
        givenPackaging("jar");

        AuthConfig authConfig = getAuthConfig();
        givenAuthConfig(authConfig);

        whenMojoExecutes();

        thenAuthMatches(authConfig);
    }

    @Test
    void buildUsingBuildxWithMultipleAuth() throws IOException, MojoExecutionException {
        Mockito.doReturn(Mockito.mock(BuildXService.class)).when(serviceHub).getBuildXService();

        givenMavenProject(buildMojo);
        givenResolvedImages(buildMojo, Collections.singletonList(singleImageWithAuthRegistry(
                BuildMojoTest.class.getResource("/docker/Dockerfile.custom_registry.test").getPath()
        )));
        givenPackaging("jar");

        givenAuthConfig(getAuthConfig(), "custom-registry.org");
        givenAuthConfig(getAuthConfig(), TEST_REGISTRY);

        whenMojoExecutes();

        thenAuthContainsRegistry(TEST_REGISTRY);
        thenAuthContainsRegistry("custom-registry.org");
    }

    private void givenBuildXService() {
        BuildXService buildXService = new BuildXService(dockerAccess, dockerAssemblyManager, log, exec);

        Mockito.doReturn(buildXService).when(serviceHub).getBuildXService();
        Mockito.doReturn(NATIVE_PLATFORM).when(dockerAccess).getNativePlatform();
    }

    private void givenAuthConfig(AuthConfig authConfig) throws MojoExecutionException {
        Mockito.doReturn(authConfig).when(buildMojo.authConfigFactory).createAuthConfig(
                Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private void givenAuthConfig(AuthConfig authConfig, String registry) throws MojoExecutionException {
        authConfig.setRegistry(registry);
        Mockito.doReturn(authConfig).when(buildMojo.authConfigFactory).createAuthConfig(
                Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(registry));
    }

    private void thenAuthMatches(AuthConfig authConfig) throws MojoExecutionException {
        ArgumentCaptor<AuthConfigList> authConfigList = ArgumentCaptor.forClass(AuthConfigList.class);
        Mockito.verify(serviceHub.getBuildXService()).build(
                Mockito.any(), Mockito.any(), Mockito.any(), authConfigList.capture(), Mockito.any());

        Assertions.assertEquals(authConfig.toJson(), authConfigList.getValue().toJson());
    }

    private void thenAuthContainsRegistry(String expectedRegistry) throws MojoExecutionException {
        ArgumentCaptor<AuthConfigList> authConfigList = ArgumentCaptor.forClass(AuthConfigList.class);
        Mockito.verify(serviceHub.getBuildXService()).build(
                Mockito.any(), Mockito.any(), Mockito.any(), authConfigList.capture(), Mockito.any());

        Assertions.assertTrue(authConfigList.getValue().toJson().contains(expectedRegistry));
    }

    private void thenBuildRun() throws DockerAccessException, MojoExecutionException {
        verifyBuild(1);
    }

    private void thenBuildNotRun() throws DockerAccessException, MojoExecutionException {
        verifyBuild(0);
    }

    private void verifyBuild(int wantedNumberOfInvocations) throws DockerAccessException, MojoExecutionException {
        Mockito.verify(buildService, Mockito.times(wantedNumberOfInvocations))
            .buildImage(Mockito.any(ImageConfiguration.class), Mockito.any(ImagePullManager.class), Mockito.any(BuildService.BuildContext.class), Mockito.any());
    }

    private void thenBuildxRun(String relativeConfigFile, String contextDir,
        boolean nativePlatformIncluded, String attestation) throws MojoExecutionException {
        Path buildPath = projectBaseDirectory.toPath().resolve("target/docker/example/latest");
        String configFile = relativeConfigFile != null ? getOsDependentBuild(projectBaseDirectory.toPath(), relativeConfigFile) : null;

        List<String> cmds =
            BuildXService.append(new ArrayList<>(), "docker",  "buildx",
                "create", "--driver", "docker-container", "--name", "maven" , "--node", "maven0");
        if (configFile != null) {
            BuildXService.append(cmds, "--config", configFile.replace('/', File.separatorChar));
        }
        Mockito.verify(exec).process(cmds);

        if (nativePlatformIncluded) {
            List<String> buildXLine = BuildXService.append(new ArrayList<>(), "docker",  "buildx",
                    "build", "--progress=plain", "--builder", "maven",
                    "--platform", NATIVE_PLATFORM, "--tag", "example:latest", "--build-arg", "foo=bar");

            if (attestation != null) {
                buildXLine.add(attestation);
            }

            if (contextDir == null) {
                buildXLine.add(getOsDependentBuild(buildPath, "build"));
            } else {
                Path contextPath = tmpDir.resolve("docker-build");
                BuildXService.append(buildXLine, "--file=" + contextPath.resolve("Dockerfile"), contextPath.toString() );
            }

            buildXLine.add("--load");
            Mockito.verify(exec).process(buildXLine);
        }
    }

    private void givenPackaging(String packaging) {
        buildMojo.packaging = packaging;
    }

    private void givenSkipPom(boolean skipPom) {
        buildMojo.skipPom = skipPom;
    }

    private void whenMojoExecutes() throws IOException, MojoExecutionException {
        buildMojo.executeInternal(serviceHub);
    }

    private BuildXConfiguration.Builder getBuildXPlatforms(String... platforms) {
        return new BuildXConfiguration.Builder()
            .nodeName("maven0")
            .platforms(Arrays.asList(platforms));
    }

    private BuildXConfiguration getBuildXConfiguration(String configFile, String... platforms) {
        return new BuildXConfiguration.Builder()
            .configFile(configFile)
            .platforms(Arrays.asList(platforms))
            .build();
    }

    private ImageConfiguration singleBuildXImageWithConfiguration(String configFile) {
        return singleImageConfiguration(
            getBuildXPlatforms(TWO_BUILDX_PLATFORMS).configFile(configFile).build(), null);
    }

    private ImageConfiguration singleBuildXImageWithContext(String contextDir) {
        return singleImageConfiguration(getBuildXPlatforms(TWO_BUILDX_PLATFORMS).build(),
            contextDir);
    }

    private ImageConfiguration singleBuildXImageNonNative() {
        return singleImageConfiguration(getBuildXPlatforms(NON_NATIVE_PLATFORM).build(), null);
    }

    private ImageConfiguration singleBuildXImageWithSquash() {
        return singleImageConfigurationWithBuildWithSquash(
            getBuildXPlatforms(TWO_BUILDX_PLATFORMS).build(), null);
    }

    private ImageConfiguration singleBuildXImageWithAttestations(Boolean sbom, String provenance) {
        return singleImageConfiguration(getBuildXPlatforms(TWO_BUILDX_PLATFORMS).attestations(
                new AttestationConfiguration.Builder().sbom(sbom).provenance(provenance).build())
            .build(), null);
    }

    protected ImageConfiguration singleImageWithAuthRegistry(String dockerFile) {
        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder()
                .dockerFile(dockerFile)
                .buildx(getBuildXPlatforms(TWO_BUILDX_PLATFORMS).build())
                .build();
        buildImageConfiguration.initAndValidate(log);

        return new ImageConfiguration.Builder()
                .name(TEST_REGISTRY + "/example:latest")
                .buildConfig(buildImageConfiguration)
                .build();
    }

    private AuthConfig getAuthConfig() {
        Map<String,String> map = new HashMap<>();
        map.put(AuthConfig.AUTH_USERNAME,"username");
        map.put(AuthConfig.AUTH_PASSWORD,"#>secrets??");
        map.put(AuthConfig.AUTH_EMAIL,"username@email.org");
        return new AuthConfig(map);
    }
}
