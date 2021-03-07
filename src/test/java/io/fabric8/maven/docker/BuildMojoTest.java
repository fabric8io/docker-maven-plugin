package io.fabric8.maven.docker;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import io.fabric8.maven.docker.access.BuildOptions;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.service.ImagePullManager;
import mockit.Deencapsulation;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

public class BuildMojoTest extends BaseMojoTest {
    @Tested(fullyInitialized = false)
    private BuildMojo buildMojo;

    @Mocked
    private BuildService buildService;

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
