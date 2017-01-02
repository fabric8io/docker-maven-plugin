package io.fabric8.maven.docker.service;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;
import mockit.integration.junit4.JMockit;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(JMockit.class)
public class LoadImageTest {
    @Tested
    private BuildService buildService;

    @Injectable
    private DockerAccess docker;

    private ImageConfiguration imageConfig;

    @Injectable
    private Logger log;

    @Mocked
    private MojoParameters params;

    @Injectable
    private QueryService queryService;

    @Injectable
    private ArchiveService archiveService;

    private String dockerArchive;


    @Test
    public void testLoadImage() throws DockerAccessException, MojoExecutionException {
        givenAnImageConfiguration();
        givenDockerArchive("test.tar");
        whenBuildImage();
        thenImageIsBuilt();
    }

    private void givenDockerArchive(String s) {
        dockerArchive = s;
    }

    private void givenAnImageConfiguration() {
        BuildImageConfiguration buildConfig = new BuildImageConfiguration.Builder()
            .dockerArchive("test.tar")
            .build();

        imageConfig = new ImageConfiguration.Builder()
            .name("build-image")
            .alias("build-alias")
            .buildConfig(buildConfig)
            .build();
    }

    private void whenBuildImage() throws DockerAccessException, MojoExecutionException {
        buildService.buildImage(imageConfig, params, false, Collections.<String, String>emptyMap());
    }

    private void thenImageIsBuilt() throws DockerAccessException {
        new Verifications() {{
            docker.loadImage("build-image", dockerArchive);
        }};
    }



}
