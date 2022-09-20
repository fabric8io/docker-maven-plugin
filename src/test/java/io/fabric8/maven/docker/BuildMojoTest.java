package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.BuildXConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.service.BuildXService;
import io.fabric8.maven.docker.service.ImagePullManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class BuildMojoTest extends MojoTestBase {
    private static final String NON_NATIVE_PLATFORM = "linux/amd64";
    private static final String NATIVE_PLATFORM = "linux/arm64";
    private static final String[] TWO_BUILDX_PLATFORMS = { NATIVE_PLATFORM, NON_NATIVE_PLATFORM };

    @InjectMocks
    private BuildMojo buildMojo;

    @Mock
    private DockerAssemblyManager dockerAssemblyManager;

    @TempDir
    private Path tmpDir;

    @Mock
    private BuildXService.Exec exec;

    private static String getOsDependentBuild(Path buildPath, String docker) {
        return buildPath.resolve(docker).toString().replace('/', File.separatorChar);
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
        givenResolvedImages(buildMojo, Collections.singletonList(singleBuildXImageWithConfiguration(null)));
        givenPackaging("jar");

        whenMojoExecutes();

        thenBuildxRun(null, null, true);
    }

    @Test
    void buildUsingConfiguredBuildx() throws IOException, MojoExecutionException {
        givenBuildXService();

        givenMavenProject(buildMojo);
        givenResolvedImages(buildMojo, Collections.singletonList(singleBuildXImageWithConfiguration("src/docker/builder.toml")));
        givenPackaging("jar");

        whenMojoExecutes();

        thenBuildxRun("src/docker/builder.toml", null, true);
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

        thenBuildxRun(null, "src/main/docker", true);
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

        thenBuildxRun(null, null, false);
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

    private void givenBuildXService() {
        BuildXService buildXService = new BuildXService(dockerAccess, dockerAssemblyManager, log, exec);

        Mockito.doReturn(buildXService).when(serviceHub).getBuildXService();
        Mockito.doReturn(NATIVE_PLATFORM).when(dockerAccess).getNativePlatform();
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

    private void thenBuildxRun(String relativeConfigFile, String contextDir, boolean nativePlatformIncluded, String... extraParams) throws MojoExecutionException {
        Path buildPath = projectBaseDirectory.toPath().resolve("target/docker/example/latest");
        String config = getOsDependentBuild(buildPath, "docker");
        String buildDir = getOsDependentBuild(buildPath, "build");
        String configFile = relativeConfigFile != null ? getOsDependentBuild(projectBaseDirectory.toPath(), relativeConfigFile) : null;
        String builderName = "maven";

        String[] cfgCmdLine = configFile == null
            ? new String[] { "create", "--driver", "docker-container", "--name", builderName }
            : new String[] { "create", "--driver", "docker-container", "--name", builderName, "--config", configFile.replace('/', File.separatorChar) };
        Mockito.verify(exec).process(Arrays.asList("docker", "--config", config, "buildx"), cfgCmdLine);

        String[] ctxCmdLine;
        if (contextDir == null) {
            ctxCmdLine = new String[] { buildDir };
        } else {
            Path contextPath = tmpDir.resolve("docker-build");
            ctxCmdLine = new String[] { "--file=" + contextPath.resolve("Dockerfile"), contextPath.toString() };
        }

        if (nativePlatformIncluded) {
            List<String> buildXLine = new ArrayList<>(Arrays.asList("docker", "--config", config, "buildx",
                    "build", "--progress=plain", "--builder", builderName,
                    "--platform", NATIVE_PLATFORM, "--tag", "example:latest", "--build-arg", "foo=bar"));
            buildXLine.addAll(Arrays.asList(extraParams));
            buildXLine.add("--load");
            Mockito.verify(exec).process(buildXLine, ctxCmdLine);
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

    private BuildXConfiguration getBuildXConfiguration(String configFile, String... platforms) {
        return new BuildXConfiguration.Builder()
            .configFile(configFile)
            .platforms(Arrays.asList(platforms))
            .build();
    }

    private ImageConfiguration singleBuildXImageWithConfiguration(String configFile) {
        return singleImageConfiguration(getBuildXConfiguration(configFile, TWO_BUILDX_PLATFORMS), null);
    }

    private ImageConfiguration singleBuildXImageWithContext(String contextDir) {
        return singleImageConfiguration(getBuildXConfiguration(null, TWO_BUILDX_PLATFORMS), contextDir);
    }

    private ImageConfiguration singleBuildXImageNonNative() {
        return singleImageConfiguration(getBuildXConfiguration(null, NON_NATIVE_PLATFORM), null);
    }

    private ImageConfiguration singleBuildXImageWithSquash() {
        return singleImageConfigurationWithBuildWithSquash(getBuildXConfiguration(null, TWO_BUILDX_PLATFORMS), null);
    }

}
