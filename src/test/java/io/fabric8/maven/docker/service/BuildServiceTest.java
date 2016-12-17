package io.fabric8.maven.docker.service;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import mockit.*;
import mockit.integration.junit4.JMockit;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMockit.class)
public class BuildServiceTest {

    private static final String NEW_IMAGE_ID = "efg789efg789";
    private static final String OLD_IMAGE_ID = "abc123abc123";

    @Tested
    private BuildService buildService;

    @Injectable
    private DockerAccess docker;

    @Mocked
    private DockerAssemblyManager dockerAssemblyManager;

    private ImageConfiguration imageConfig;

    @Injectable
    private Logger log;

    private String oldImageId;

    @Mocked
    private MojoParameters params;

    @Injectable
    private QueryService queryService;

    @Injectable
    private ArchiveService archiveService;

    @Before
    public void setup() throws Exception {
        new Expectations() {{
            archiveService.createArchive(anyString, (BuildImageConfiguration) any, (MojoParameters) any, log);
            result = new File("docker-build.tar");
        }};
    }

    @Test
    public void testBuildImageWithCleanup() throws Exception {
        givenAnImageConfiguration(true);
        givenImageIds(OLD_IMAGE_ID, NEW_IMAGE_ID);
        whenBuildImage(true,false);
        thenImageIsBuilt();
        thenOldImageIsRemoved();
    }

    @Test
    public void testBuildImageWithNoCleanup() throws Exception {
        givenAnImageConfiguration(false);
        givenImageIds(OLD_IMAGE_ID, NEW_IMAGE_ID);
        whenBuildImage(false,false);
        thenImageIsBuilt();
        thenOldImageIsNotRemoved();
    }

    @Test
    public void testCleanupCachedImage() throws Exception {
        givenAnImageConfiguration(true);
        givenImageIds(OLD_IMAGE_ID, OLD_IMAGE_ID);
        whenBuildImage(false, false);
        thenImageIsBuilt();
        thenOldImageIsNotRemoved();
    }

    @Test
    public void testCleanupNoExistingImage() throws Exception {
        givenAnImageConfiguration(true);
        givenImageIds(null, NEW_IMAGE_ID);
        whenBuildImage(false, false);
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

    private void givenImageIds(final String oldImageId, final String newImageId) throws DockerAccessException {
        this.oldImageId = oldImageId;
        new Expectations() {{
            queryService.getImageId(imageConfig.getName()); result = new String[] { oldImageId, newImageId };
        }};
    }

    private void thenImageIsBuilt() throws DockerAccessException {
        final File dockerBuildTar = new File("docker-build.tar");
        new Verifications() {{
            docker.buildImage(imageConfig.getName(),
                              dockerBuildTar,
                              (String) withNull(),
                              anyBoolean, anyBoolean, (Map) any);
        }};
    }

    private void thenOldImageIsNotRemoved() throws DockerAccessException {
        new FullVerifications(docker) {{

        }};
    }

    private void thenOldImageIsRemoved() throws DockerAccessException {
        new Verifications() {{
            docker.removeImage(oldImageId, true);
        }};
    }

    private void whenBuildImage(boolean cleanup, boolean nocache) throws DockerAccessException, MojoExecutionException {
        new Expectations() {{
            docker.buildImage(withEqual(imageConfig.getName()), (File) any, (String) withNull(), anyBoolean, anyBoolean, (Map) any);
        }};
        if (cleanup) {
            new Expectations() {{
                docker.removeImage(withEqual(oldImageId), withEqual(true));result = true;
            }};
        }

        buildService.buildImage(imageConfig, params, nocache, Collections.<String, String>emptyMap());

    }
}
