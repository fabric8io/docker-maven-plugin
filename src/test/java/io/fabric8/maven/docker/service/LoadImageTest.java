package io.fabric8.maven.docker.service;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ConfigHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.Collections;

@ExtendWith(MockitoExtension.class)
class LoadImageTest {
    @InjectMocks
    private BuildService buildService;

    @Mock
    private DockerAccess docker;

    private ImageConfiguration imageConfig;

    @Mock
    private Logger log;

    @Mock
    private MavenProject project;

    @Mock
    private MojoParameters params;

    @Mock
    private QueryService queryService;

    @Mock
    private ArchiveService archiveService;

    @Mock
    private RegistryService registryService;

    private String dockerArchive;

    @Test
    void testLoadImage() throws DockerAccessException, MojoExecutionException {
        givenMojoParameters();
        givenAnImageConfiguration();
        givenDockerArchive("test.tar");
        whenBuildImage();
        thenImageIsBuilt();
    }

    private void givenMojoParameters() {
        Mockito.doReturn(project).when(params).getProject();
        Mockito.doReturn(new File("/maven-project")).when(project).getBasedir();
        Mockito.doReturn("src/main/docker").when(params).getSourceDirectory();
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
        buildService.buildImage(imageConfig, params, false, false, Collections.emptyMap(), new File("/maven-project/src/main/docker/test.tar"));
    }

    private void thenImageIsBuilt() throws DockerAccessException {
        final File targetFile = new File("/maven-project/src/main/docker/test.tar");
        Mockito.verify(docker).loadImage("build-image", targetFile);
    }

}
