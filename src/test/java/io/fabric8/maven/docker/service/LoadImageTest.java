package io.fabric8.maven.docker.service;

import java.io.File;
import java.util.Collections;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ConfigHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Test;


public class LoadImageTest {
    @Tested
    private BuildService buildService;

    @Injectable
    private DockerAccess docker;

    private ImageConfiguration imageConfig;

    @Injectable
    private Logger log;

    @Mocked
    private MavenProject project;

    @Mocked
    private MojoParameters params;

    @Injectable
    private QueryService queryService;

    @Injectable
    private ArchiveService archiveService;

    @Injectable
    private RegistryService registryService;

    private String dockerArchive;

    @Test
    public void testLoadImage() throws DockerAccessException, MojoExecutionException {
        givenMojoParameters();
        givenAnImageConfiguration();
        givenDockerArchive("test.tar");
        whenBuildImage();
        thenImageIsBuilt();
    }

    private void givenMojoParameters() {
        new Expectations() {{
            params.getProject();
            project.getBasedir(); result = "/maven-project";
            params.getSourceDirectory(); result = "src/main/docker";
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

    private void whenBuildImage() throws DockerAccessException, MojoExecutionException {
        buildService.buildImage(imageConfig, params, false, Collections.<String, String>emptyMap());
    }

    private void thenImageIsBuilt() throws DockerAccessException {
        final File targetFile = new File("/maven-project/src/main/docker/test.tar");
        new Verifications() {{
            docker.loadImage("build-image", withEqual(targetFile));
        }};
    }



}
