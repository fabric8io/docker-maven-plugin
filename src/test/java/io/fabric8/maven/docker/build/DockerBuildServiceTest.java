package io.fabric8.maven.docker.build;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import io.fabric8.maven.docker.access.BuildOptions;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.build.docker.DockerBuildService;
import io.fabric8.maven.docker.build.docker.DockerRegistryService;
import io.fabric8.maven.docker.build.maven.MavenArchiveService;
import io.fabric8.maven.docker.build.maven.MavenBuildContext;
import io.fabric8.maven.docker.build.maven.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.build.BuildConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.QueryService;
import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.FullVerifications;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;
import org.junit.Test;


public class DockerBuildServiceTest {

    private static final String NEW_IMAGE_ID = "efg789efg789";
    private static final String OLD_IMAGE_ID = "abc123abc123";

    @Tested
    private DockerBuildService buildService;

    @Injectable
    private DockerAccess docker;

    @Mocked
    private DockerAssemblyManager dockerAssemblyManager;

    private ImageConfiguration imageConfig;

    @Injectable
    private Logger log;

    private String oldImageId;

    @Mocked
    private MavenBuildContext buildContext;

    @Injectable
    private QueryService queryService;

    @Injectable
    private MavenArchiveService archiveService;

    @Injectable
    private DockerRegistryService registryService;


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
        whenBuildImage(false);
        thenImageIsBuilt();
        thenOldImageIsNotRemoved();
    }

    @Test
    public void testCleanupNoExistingImage() throws Exception {
        givenAnImageConfiguration(true);
        givenImageIds(null, NEW_IMAGE_ID);
        whenBuildImage(false);
        thenImageIsBuilt();
        thenOldImageIsNotRemoved();
    }

    private void givenAnImageConfiguration(Boolean cleanup) {
        BuildConfiguration buildConfig = new BuildConfiguration.Builder()
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
            docker.getImageId(imageConfig.getName()); result = new String[] { oldImageId, newImageId };
        }};
    }

    private void thenImageIsBuilt() throws DockerAccessException {
        final File dockerBuildTar = new File("docker-build.tar");
        new Verifications() {{
            docker.buildImage(imageConfig.getName(),
                              dockerBuildTar,
                              (BuildOptions) any);
        }};
    }

    private void thenOldImageIsNotRemoved() {
        new FullVerifications(docker) {{

        }};
    }

    private void thenOldImageIsRemoved() throws DockerAccessException {
        new Verifications() {{
            docker.removeImage(oldImageId, true);
        }};
    }

    private void whenBuildImage(boolean cleanup) throws IOException {
        new Expectations() {{
            docker.buildImage(withEqual(imageConfig.getName()), (File) any, (BuildOptions) any);
            buildContext.createImageContentArchive(withEqual(imageConfig.getName()), (BuildConfiguration) any, withEqual(log));
            result = new File("docker-build.tar");
        }};
        if (cleanup) {
            new Expectations() {{
                docker.removeImage(withEqual(oldImageId), withEqual(true)); result = true;
            }};
        }

        buildService.buildImage(imageConfig, buildContext, Collections.emptyMap());

    }
}
