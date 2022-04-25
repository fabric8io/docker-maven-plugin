package io.fabric8.maven.docker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.BuildOptions;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.service.BuildXService;
import io.fabric8.maven.docker.service.ImagePullManager;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;
import mockit.VerificationsInOrder;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Settings;
import org.junit.Test;

public class BuildMojoTest extends BaseMojoTest {
    @Tested(fullyInitialized = false)
    private BuildMojo buildMojo;

    @Mocked
    private BuildService buildService;

    @Mocked
    private BuildXService.Exec exec;

    @Test
    public void skipWhenPom() throws IOException, MojoExecutionException {
        Deencapsulation.setField(serviceHub, "buildService", buildService);
        givenMavenProject(buildMojo);
        givenPackaging("pom");
        givenSkipPom(true);

        whenMojoExecutes();

        thenBuildNotRun();
    }

    @Test
    public void noSkipWhenNotPom() throws IOException, MojoExecutionException {
        Deencapsulation.setField(serviceHub, "buildService", buildService);
        givenMavenProject(buildMojo);
        givenResolvedImages(buildMojo, Collections.singletonList(singleImageWithBuild()));
        givenPackaging("jar");
        givenSkipPom(true);

        whenMojoExecutes();

        thenBuildRun();
    }

    @Test
    public void buildUsingBuildx() throws IOException, MojoExecutionException {
        BuildXService buildXService= new BuildXService(dockerAccess, log, exec);

        new Expectations() {{
            serviceHub.getBuildXService();
            result= buildXService;
            authConfigFactory.createAuthConfig(false, false, null, null, null, null);
            result = null;
            dockerAccess.getNativePlatform();
            result = "linux/amd64";
        }};

        givenMavenProject(buildMojo);
        givenResolvedImages(buildMojo, Collections.singletonList(singleBuildXImage()));
        givenPackaging("jar");

        whenMojoExecutes();

        thenBuildxRun();
    }

    private void thenBuildRun() throws DockerAccessException, MojoExecutionException {
        new Verifications() {{
            buildService.buildImage((ImageConfiguration)any, (ImagePullManager) any, (BuildService.BuildContext)any, (File)any); times = 1;
        }};
    }

    private void thenBuildNotRun() throws DockerAccessException, MojoExecutionException {
        new Verifications() {{
            buildService.buildImage((ImageConfiguration)any, (ImagePullManager) any, (BuildService.BuildContext)any, (File)any); times = 0;
        }};
    }

    private void thenBuildxRun() throws MojoExecutionException {
        Path buildPath = Paths.get(projectBaseDirectory).resolve("target/docker/example/latest");
        String config = getOsDependentBuild(buildPath, "docker");
        String cacheDir = getOsDependentBuild(buildPath, "cache");
        String buildDir = getOsDependentBuild(buildPath, "build");
        String builderName = "dmp_example_latest";

        new VerificationsInOrder() {{
            exec.process(Arrays.asList("docker", "--config", config, "buildx"),
                "create", "--driver", "docker-container", "--name", builderName);

            exec.process(Arrays.asList("docker", "--config", config, "buildx",
                "build", "--progress=plain", "--builder", builderName,
                "--platform", "linux/amd64,linux/arm64", "--tag", "example:latest",
                "--cache-to=type=local,dest=" + cacheDir, "--cache-from=type=local,src=" + cacheDir,
                buildDir));

            exec.process(Arrays.asList("docker", "--config", config, "buildx",
                "build", "--progress=plain", "--builder", builderName,
                "--platform", "linux/amd64", "--tag", "example:latest",
                "--load",
                "--cache-to=type=local,dest=" + cacheDir, "--cache-from=type=local,src=" + cacheDir,
                buildDir));

            exec.process(Arrays.asList("docker", "--config", config, "buildx"),
                "rm", builderName);
        }};
    }

    private static String getOsDependentBuild(Path buildPath, String docker) {
        return buildPath.resolve(docker).toString().replace('/', File.separatorChar);
    }

    private void givenPackaging(String packaging) {
        Deencapsulation.setField(buildMojo, "packaging", packaging);
    }

    private void givenSkipPom(boolean skipPom) {
        Deencapsulation.setField(buildMojo, "skipPom", skipPom);
    }

    private void whenMojoExecutes() throws IOException, MojoExecutionException {
        buildMojo.executeInternal(serviceHub);
    }
}
