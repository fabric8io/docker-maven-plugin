package io.fabric8.maven.docker.service;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.build.docker.DockerBuildService;
import io.fabric8.maven.docker.build.docker.DockerRegistryService;
import io.fabric8.maven.docker.build.maven.MavenArchiveService;
import io.fabric8.maven.docker.build.maven.MavenBuildContext;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ConfigHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;
import org.apache.maven.project.MavenProject;
import org.junit.Test;


public class LoadImageTest {
    @Tested
    private DockerBuildService buildService;

    @Injectable
    private DockerAccess docker;

    private ImageConfiguration imageConfig;

    @Injectable
    private Logger log;

    @Mocked
    private MavenProject project;

    @Mocked
    private MavenBuildContext buildContext;

    @Injectable
    private QueryService queryService;

    @Injectable
    private MavenArchiveService archiveService;

    @Injectable
    private DockerRegistryService registryService;

    private String dockerArchive;

    @Test
    public void testLoadImage() throws IOException {
        givenBuildContext();
        givenAnImageConfiguration();
        givenDockerArchive("test.tar");
        whenBuildImage();
        thenImageIsBuilt();
    }

    private void givenBuildContext() {
        new Expectations() {{
            buildContext.getBasedir(); result = "/maven-project";
            buildContext.getSourceDirectory();result = "src/main/docker";
        }};
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
        imageConfig.initAndValidate(ConfigHelper.NameFormatter.IDENTITY,log);
    }

    private void whenBuildImage() throws IOException {
        buildService.buildImage(imageConfig, buildContext, Collections.emptyMap());
    }

    private void thenImageIsBuilt() throws DockerAccessException {
        final File targetFile = new File("/maven-project/src/main/docker/test.tar");
        new Verifications() {{
            docker.loadImage("build-image", withEqual(targetFile));
        }};
    }



}
