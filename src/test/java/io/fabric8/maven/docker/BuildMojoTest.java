package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
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
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

@ExtendWith(MockitoExtension.class)
class BuildMojoTest extends MojoTestBase {
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

        thenBuildxRun(null, null);
    }

    @Test
    void buildUsingConfiguredBuildx() throws IOException, MojoExecutionException {
        givenBuildXService();

        givenMavenProject(buildMojo);
        givenResolvedImages(buildMojo, Collections.singletonList(singleBuildXImageWithConfiguration("src/docker/builder.toml")));
        givenPackaging("jar");

        whenMojoExecutes();

        thenBuildxRun("src/docker/builder.toml", null);
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

        thenBuildxRun(null, "src/main/docker");
    }

    private void givenBuildXService() {
        BuildXService buildXService = new BuildXService(dockerAccess, dockerAssemblyManager, log, exec);

        Mockito.doReturn(buildXService).when(serviceHub).getBuildXService();
        Mockito.doReturn("linux/amd64").when(dockerAccess).getNativePlatform();
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

    private void thenBuildxRun(String relativeConfigFile, String contextDir) throws MojoExecutionException {
        Path buildPath = projectBaseDirectory.toPath().resolve("target/docker/example/latest");
        String config = getOsDependentBuild(buildPath, "docker");
        String cacheDir = getOsDependentBuild(buildPath, "cache");
        String buildDir = getOsDependentBuild(buildPath, "build");
        String configFile = relativeConfigFile != null ? getOsDependentBuild(projectBaseDirectory.toPath(), relativeConfigFile) : null;
        String builderName = "dmp_example_latest";

        String[] cfgCmdLine = configFile == null
            ? new String[] { "create", "--driver", "docker-container", "--name", builderName }
            : new String[] { "create", "--driver", "docker-container", "--name", builderName, "--config", configFile.replace('/', File.separatorChar) };
        Mockito.verify(exec).process(Arrays.asList("docker", "--config", config, "buildx"), cfgCmdLine);

        String[] ctxCmdLine;
        if (contextDir == null) {
            ctxCmdLine = new String[] { buildDir };
        } else {
            Path contextPath = tmpDir.resolve("docker-build");
            ctxCmdLine = new String[] { "--file=" +  contextPath.resolve("Dockerfile"), contextPath.toString()};
        }

        Mockito.verify(exec).process(Arrays.asList("docker", "--config", config, "buildx",
            "build", "--progress=plain", "--builder", builderName,
            "--platform", "linux/amd64,linux/arm64", "--tag", "example:latest", "--build-arg", "foo=bar",
            "--cache-to=type=local,dest=" + cacheDir, "--cache-from=type=local,src=" + cacheDir), ctxCmdLine);

        Mockito.verify(exec).process(Arrays.asList("docker", "--config", config, "buildx",
            "build", "--progress=plain", "--builder", builderName,
            "--platform", "linux/amd64", "--tag", "example:latest", "--build-arg", "foo=bar",
            "--load",
            "--cache-to=type=local,dest=" + cacheDir, "--cache-from=type=local,src=" + cacheDir), ctxCmdLine);

        Mockito.verify(exec).process(Arrays.asList("docker", "--config", config, "buildx"),
            "rm", builderName);
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
}
