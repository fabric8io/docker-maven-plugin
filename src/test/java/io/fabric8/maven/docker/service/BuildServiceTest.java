package io.fabric8.maven.docker.service;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import java.util.List;
import java.util.Properties;


import io.fabric8.maven.docker.access.BuildOptions;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.CleanupMode;
import io.fabric8.maven.docker.util.DockerFileUtilTest;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import mockit.Expectations;
import mockit.FullVerifications;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;


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

    @Mocked
    Logger logger;

    @Mocked
    MojoParameters mojoParameters;

    @Mocked
    MavenProject mavenProject;

    @Injectable
    private QueryService queryService;

    @Injectable
    private ArchiveService archiveService;

    @Injectable
    private RegistryService registryService;

    @Before
    public void setup() throws Exception {
        new Expectations() {{
            archiveService.createArchive(anyString, (BuildImageConfiguration) any, (MojoParameters) any, log);
            result = new File("docker-build.tar");
        }};
    }

    @Test
    public void testBuildImageWithCleanup() throws Exception {
        givenAnImageConfiguration(Boolean.TRUE.toString());
        givenImageIds(OLD_IMAGE_ID, NEW_IMAGE_ID);
        whenBuildImage(true,false);
        thenImageIsBuilt();
        thenOldImageIsRemoved();
    }

    @Test
    public void testBuildImageWithNoCleanup() throws Exception {
        givenAnImageConfiguration(Boolean.FALSE.toString());
        givenImageIds(OLD_IMAGE_ID, NEW_IMAGE_ID);
        whenBuildImage(false,false);
        thenImageIsBuilt();
        thenOldImageIsNotRemoved();
    }

    @Test
    public void testCleanupCachedImage() throws Exception {
        givenAnImageConfiguration(Boolean.TRUE.toString());
        givenImageIds(OLD_IMAGE_ID, OLD_IMAGE_ID);
        whenBuildImage(false, false);
        thenImageIsBuilt();
        thenOldImageIsNotRemoved();
    }

    @Test
    public void testCleanupNoExistingImage() throws Exception {
        givenAnImageConfiguration(Boolean.TRUE.toString());
        givenImageIds(null, NEW_IMAGE_ID);
        whenBuildImage(false, false);
        thenImageIsBuilt();
        thenOldImageIsNotRemoved();
    }

    @Test
    public void testMultiStageBuild() throws Exception {
        BuildImageConfiguration buildConfig = new BuildImageConfiguration.Builder()
                .cleanup("false")
                .dockerFile(DockerFileUtilTest.class.getResource("Dockerfile_multi_stage").getPath())
                .filter("false")
                .build();

        buildConfig.initAndValidate(logger);

        imageConfig = new ImageConfiguration.Builder()
                .name("build-image")
                .alias("build-alias")
                .buildConfig(buildConfig)
                .build();

        final ImagePullManager pullManager = new ImagePullManager(null,null, null);
        final BuildService.BuildContext buildContext = new BuildService.BuildContext.Builder()
                .mojoParameters(mojoParameters)
                .build();

        new Expectations(mojoParameters) {{
            mojoParameters.getProject(); result = mavenProject;
            mavenProject.getProperties(); result = new Properties();
        }};

        File buildArchive = buildService.buildArchive(imageConfig, buildContext, "");
        buildService.buildImage(imageConfig, pullManager, buildContext, buildArchive);

        //verify that tries to pull both images
        new Verifications() {{
            registryService.pullImageWithPolicy("fabric8/s2i-java",  pullManager, buildContext.getRegistryConfig(), buildConfig);
            registryService.pullImageWithPolicy("fabric8/s1i-java",  pullManager, buildContext.getRegistryConfig(), buildConfig);
        }};
    }

    @Test
    public void testBuildImageWithCacheFrom_ShouldPullImage() throws Exception {
        BuildImageConfiguration buildConfig = new BuildImageConfiguration.Builder()
                .cleanup("false")
                .cacheFrom("fabric8/s1i-java")
                .dockerFile(DockerFileUtilTest.class.getResource("Dockerfile_from_simple").getPath())
                .filter("false")
                .build();

        buildConfig.initAndValidate(logger);

        imageConfig = new ImageConfiguration.Builder()
                .name("build-image")
                .alias("build-alias")
                .buildConfig(buildConfig)
                .build();

        final ImagePullManager pullManager = new ImagePullManager(null,null, null);
        final BuildService.BuildContext buildContext = new BuildService.BuildContext.Builder()
                .mojoParameters(mojoParameters)
                .build();

        new Expectations(mojoParameters) {{
            mojoParameters.getProject(); result = mavenProject;
            mavenProject.getProperties(); result = new Properties();
        }};

        File buildArchive = buildService.buildArchive(imageConfig, buildContext, "");
        buildService.buildImage(imageConfig, pullManager, buildContext, buildArchive);

        //verify that tries to pull both images
        new Verifications() {{
            registryService.pullImageWithPolicy("fabric8/s2i-java",  pullManager, buildContext.getRegistryConfig(), buildConfig);
            registryService.pullImageWithPolicy("fabric8/s1i-java",  pullManager, buildContext.getRegistryConfig(), buildConfig);
        }};
    }

    @Test
    public void testDockerBuildArchiveOnly() throws Exception {
        givenAnImageConfiguration(Boolean.TRUE.toString());
        final BuildService.BuildContext buildContext = new BuildService.BuildContext.Builder()
                .mojoParameters(mojoParameters)
                .build();
        File dockerArchive = buildService.buildArchive(imageConfig, buildContext, mavenProject.getBasedir().getAbsolutePath());
        assertNotNull(dockerArchive);
    }

    @Test (expected = MojoExecutionException.class)
    public void testDockerBuildArchiveOnlyWithInvalidPath() throws MojoExecutionException{
        givenAnImageConfiguration(Boolean.TRUE.toString());
        final BuildService.BuildContext buildContext = new BuildService.BuildContext.Builder()
                .mojoParameters(mojoParameters)
                .build();
        File dockerArchive = buildService.buildArchive(imageConfig, buildContext, "/i/donot/exist");
        assertNotNull(dockerArchive);
    }

    @Test
    public void testTagImage() throws DockerAccessException, MojoExecutionException {
        // Given
        givenAnImageConfiguration(Boolean.FALSE.toString());
        final BuildService.BuildContext buildContext = new BuildService.BuildContext.Builder()
                .mojoParameters(mojoParameters)
                .build();

        // When
        whenBuildImage(false, true);
        buildService.tagImage(imageConfig.getName(), "1.1.0", "quay.io/someuser", imageConfig.getBuildConfiguration().cleanupMode());

        // Then
        thenImageIsBuilt();
        new Verifications() {{
            docker.tag(imageConfig.getName(), "quay.io/someuser/build-image:1.1.0", true); times = 1;
        }};
    }

    @Test
    public void tagImage_whenForceFalseAndDanglingImagesTagsPresent_thenImageNotRemoved() throws DockerAccessException, MojoExecutionException {
        // Given
        givenAnImageConfiguration(CleanupMode.REMOVE.toParameter());
        givenImageIds(OLD_IMAGE_ID, NEW_IMAGE_ID);
        givenImageWithIdBeforeAndAfterTag("quay.io/someuser/build-image:1.1.0", "oldimage", "newimage");
        givenImageTags("oldimage", Arrays.asList("t1", "t2"));

        // When
        whenBuildImage(true, true);
        buildService.tagImage(imageConfig.getName(), "1.1.0", "quay.io/someuser", imageConfig.getBuildConfiguration().cleanupMode());

        // Then
        thenImageIsBuilt();
        new Verifications() {{
            docker.tag(imageConfig.getName(), "quay.io/someuser/build-image:1.1.0", true); times = 1;
            docker.removeImage(withEqual("oldimage"), anyBoolean); times = 0;
        }};
    }

    @Test
    public void tagImage_whenForceFalseAndNoDanglingTags_thenImageRemoved() throws DockerAccessException, MojoExecutionException {
        // Given
        givenAnImageConfiguration(CleanupMode.REMOVE.toParameter());
        givenImageIds(OLD_IMAGE_ID, NEW_IMAGE_ID);
        givenImageWithIdBeforeAndAfterTag("quay.io/someuser/build-image:1.1.0", "oldimage", "newimage");
        givenImageTags("oldimage", Collections.emptyList());

        // When
        whenBuildImage(true, true);
        buildService.tagImage(imageConfig.getName(), "1.1.0", "quay.io/someuser", imageConfig.getBuildConfiguration().cleanupMode());

        // Then
        thenImageIsBuilt();
        new Verifications() {{
            docker.tag(imageConfig.getName(), "quay.io/someuser/build-image:1.1.0", true); times = 1;
            docker.removeImage(withEqual("oldimage"), anyBoolean); times = 1;
        }};
    }

    private void givenAnImageConfiguration(String cleanup) {
        BuildImageConfiguration buildConfig = new BuildImageConfiguration.Builder()
                .cleanup(cleanup)
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

    private void givenImageWithIdBeforeAndAfterTag(final String name, final String oldImageId, final String newImageId) throws DockerAccessException {
        new Expectations() {{
            queryService.getImageId(name);
            result = oldImageId;
            result = newImageId;
            docker.getImageTags("oldimage");
            result = Collections.emptyList();
        }};
    }

    private void givenImageTags(final String name, List<String> imageTags) throws DockerAccessException {
        new Expectations() {{
            docker.getImageTags(name);
            result = imageTags;
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
            docker.buildImage(withEqual(imageConfig.getName()), (File) any, (BuildOptions) any);
        }};
        if (cleanup) {
            new Expectations() {{
                docker.removeImage(withEqual(oldImageId), withEqual(true)); result = true;
            }};
        }
        final BuildService.BuildContext buildContext = new BuildService.BuildContext.Builder()
                .mojoParameters(mojoParameters)
                .build();
        File dockerArchive = buildService.buildArchive(imageConfig, buildContext, "");

        buildService.buildImage(imageConfig, params, nocache, false, Collections.<String, String>emptyMap(), dockerArchive);

    }
}
