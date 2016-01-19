package org.jolokia.docker.maven.service;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.assembly.DockerAssemblyManager;
import org.jolokia.docker.maven.config.BuildImageConfiguration;
import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.util.Logger;
import org.jolokia.docker.maven.util.MojoParameters;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.*;

public class BuildServiceTest {

    private static final String NEW_IMAGE_ID = "efg789efg789";
    private static final String OLD_IMAGE_ID = "abc123abc123";

    private BuildService buildService;

    @Mock
    private DockerAccess docker;

    @Mock
    private DockerAssemblyManager dockerAssemblyManager;

    private ImageConfiguration imageConfig;

    @Mock
    private Logger log;

    private String oldImageId;

    @Mock
    private MojoParameters params;

    @Mock
    private QueryService queryService;

    @Mock
    private ArchiveService archiveService;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(dockerAssemblyManager.createDockerTarArchive(anyString(), any(MojoParameters.class), any(BuildImageConfiguration.class)))
                .thenReturn(null);

        buildService = new BuildService(docker, queryService, archiveService, log);
    }

    @Test
    public void testBuildImageWithCleanup() throws Exception {
        givenAnImageConfiguration(true);
        givenImageIds(OLD_IMAGE_ID, NEW_IMAGE_ID);
        whenBuildImage(true);
        thenImageIsBuilt();
        thenOldImageIsRemoved();
    }

    @Test
    public void testBuildImageWithNoCleanup() throws Exception {
        givenAnImageConfiguration(false);
        givenImageIds(OLD_IMAGE_ID, NEW_IMAGE_ID);
        whenBuildImage(false);
        thenImageIsBuilt();
        thenOldImageIsNotRemoved();
    }
    
    @Test
    public void testCleanupCachedImage() throws Exception {
        givenAnImageConfiguration(true);
        givenImageIds(OLD_IMAGE_ID, OLD_IMAGE_ID);
        whenBuildImage(true);
        thenImageIsBuilt();
        thenOldImageIsNotRemoved();
    }
    
    @Test
    public void testCleanupNoExistingImage() throws Exception {
        givenAnImageConfiguration(true);
        givenImageIds(null, NEW_IMAGE_ID);
        whenBuildImage(true);
        thenImageIsBuilt();
        thenOldImageIsNotRemoved();
    }
    
    private void givenAnImageConfiguration(Boolean cleanup) {
        BuildImageConfiguration buildConfig = new BuildImageConfiguration.Builder()
                .cleanup(cleanup.toString())
                .build();
        
        imageConfig = new ImageConfiguration.Builder()
                .name("build-image")
                .alias("build-alias")
                .buildConfig(buildConfig)
                .build();
    }

    private void givenImageIds(String oldImageId, String newImageId) throws DockerAccessException {
        this.oldImageId = oldImageId;
        when(queryService.getImageId(imageConfig.getName())).thenReturn(oldImageId).thenReturn(newImageId);
    }

    private void thenImageIsBuilt() throws DockerAccessException {
        verify(docker).buildImage(eq(imageConfig.getName()), (File) eq(null), anyBoolean(), anyBoolean());
    }

    private void thenOldImageIsNotRemoved() throws DockerAccessException {
        verify(docker, never()).removeImage(oldImageId);
    }

    private void thenOldImageIsRemoved() throws DockerAccessException {
        verify(docker).removeImage(oldImageId,true);
    }

    private void whenBuildImage(boolean cleanup) throws DockerAccessException, MojoExecutionException {
        doNothing().when(docker).buildImage(eq(imageConfig.getName()), (File) isNull(), anyBoolean(), anyBoolean());

        if (cleanup) {
            when(docker.removeImage(oldImageId)).thenReturn(true);
        }

        buildService.buildImage(imageConfig, params);
    }
}
