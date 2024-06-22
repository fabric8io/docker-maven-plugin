package io.fabric8.maven.docker.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import io.fabric8.maven.docker.access.BuildOptions;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.CleanupMode;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;

@ExtendWith(MockitoExtension.class)
class BuildServiceTest {

    private static final String NEW_IMAGE_ID = "efg789efg789";
    private static final String OLD_IMAGE_ID = "abc123abc123";

    @InjectMocks
    private BuildService buildService;

    @Mock
    private DockerAccess docker;

    private ImageConfiguration imageConfig;

    @Mock
    private Logger log;

    private String oldImageId;

    @Mock
    private MojoParameters params;

    @Mock
    Logger logger;

    @Mock
    MojoParameters mojoParameters;

    @Mock
    MavenProject mavenProject;

    @Mock
    MavenSession mavenSession;

    @Mock
    private QueryService queryService;

    @Mock
    private ArchiveService archiveService;

    @Mock
    private RegistryService registryService;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setup() throws Exception {
        Mockito.doReturn(tempDir.resolve("docker-build.tar").toFile())
            .when(archiveService)
            .createArchive(Mockito.anyString(), Mockito.any(BuildImageConfiguration.class), Mockito.any(MojoParameters.class), Mockito.any(Logger.class));
    }

    @Test
    void testBuildImageWithCleanup() throws Exception {
        givenAnImageConfiguration(Boolean.TRUE.toString());
        givenImageIds(OLD_IMAGE_ID, NEW_IMAGE_ID);
        whenBuildImage(true, false);
        thenImageIsBuilt();
        thenOldImageIsRemoved();
    }

    @Test
    void testBuildImageWithNoCleanup() throws Exception {
        givenAnImageConfiguration(Boolean.FALSE.toString());
        givenImageIds(OLD_IMAGE_ID, NEW_IMAGE_ID);
        whenBuildImage(false, false);
        thenImageIsBuilt();
        thenOldImageIsNotRemoved();
    }

    @Test
    void testCleanupCachedImage() throws Exception {
        givenAnImageConfiguration(Boolean.TRUE.toString());
        givenImageIds(OLD_IMAGE_ID, OLD_IMAGE_ID);
        whenBuildImage(false, false);
        thenImageIsBuilt();
        thenOldImageIsNotRemoved();
    }

    @Test
    void testCleanupNoExistingImage() throws Exception {
        givenAnImageConfiguration(Boolean.TRUE.toString());
        givenImageIds(null, NEW_IMAGE_ID);
        whenBuildImage(false, false);
        thenImageIsBuilt();
        thenOldImageIsNotRemoved();
    }

    @Test
    void testMultiStageBuild() throws Exception {
        BuildImageConfiguration buildConfig = new BuildImageConfiguration.Builder()
            .cleanup("false")
            .dockerFile(getClass().getResource("/io/fabric8/maven/docker/util/Dockerfile_multi_stage").getPath())
            .filter("false")
            .build();

        buildConfig.initAndValidate(logger);

        imageConfig = new ImageConfiguration.Builder()
            .name("build-image")
            .alias("build-alias")
            .buildConfig(buildConfig)
            .build();

        final ImagePullManager pullManager = new ImagePullManager(null, null, null);
        final BuildService.BuildContext buildContext = new BuildService.BuildContext.Builder()
            .mojoParameters(mojoParameters)
            .build();

        mockMavenProject();

        File buildArchive = buildService.buildArchive(imageConfig, buildContext, "");
        buildService.buildImage(imageConfig, pullManager, buildContext, buildArchive);

        //verify that tries to pull both images
        verifyImagePull(buildConfig, pullManager, buildContext, "fabric8/s2i-java");
        verifyImagePull(buildConfig, pullManager, buildContext, "fabric8/s1i-java");
    }

    private void verifyImagePull(BuildImageConfiguration buildConfig, ImagePullManager pullManager, BuildService.BuildContext buildContext, String image)
        throws DockerAccessException, MojoExecutionException {
        Mockito.verify(registryService).
            pullImageWithPolicy(image, pullManager, buildContext.getRegistryConfig(), buildConfig);
    }

    private void mockMavenProject() {
        Mockito.doReturn(mavenProject).when(mojoParameters).getProject();
        Mockito.doReturn(new Properties()).when(mavenProject).getProperties();
    }

    @Test
    void testBuildImageWithCacheFrom_ShouldPullImage() throws Exception {
        BuildImageConfiguration buildConfig = new BuildImageConfiguration.Builder()
            .cleanup("false")
            .cacheFrom("fabric8/s1i-java")
            .dockerFile(getClass().getResource("/io/fabric8/maven/docker/util/Dockerfile_from_simple").getPath())
            .filter("false")
            .build();

        buildConfig.initAndValidate(logger);

        imageConfig = new ImageConfiguration.Builder()
            .name("build-image")
            .alias("build-alias")
            .buildConfig(buildConfig)
            .build();

        final ImagePullManager pullManager = new ImagePullManager(null, null, null);
        final BuildService.BuildContext buildContext = new BuildService.BuildContext.Builder()
            .mojoParameters(mojoParameters)
            .build();

        mockMavenProject();

        File buildArchive = buildService.buildArchive(imageConfig, buildContext, "");
        buildService.buildImage(imageConfig, pullManager, buildContext, buildArchive);

        //verify that tries to pull both images
        verifyImagePull(buildConfig, pullManager, buildContext, "fabric8/s2i-java");
        verifyImagePull(buildConfig, pullManager, buildContext, "fabric8/s1i-java");
    }

    @Test
    void testDockerfileWithBuildArgsInBuildConfig_ShouldPullImage() throws Exception {
        BuildImageConfiguration buildConfig = new BuildImageConfiguration.Builder()
            .dockerFile(getClass().getResource("/io/fabric8/maven/docker/util/Dockerfile_from_build_arg").getPath())
            .args(Collections.singletonMap("FROM_IMAGE", "sample/base-image:latest"))
            .build();

        buildConfig.initAndValidate(logger);

        imageConfig = new ImageConfiguration.Builder()
            .name("build-image")
            .buildConfig(buildConfig)
            .build();

        final ImagePullManager pullManager = new ImagePullManager(null, null, null);
        final BuildService.BuildContext buildContext = new BuildService.BuildContext.Builder()
            .mojoParameters(mojoParameters)
            .build();

        Mockito.when(mojoParameters.getSession()).thenReturn(mavenSession);
        mockMavenProject();

        File buildArchive = buildService.buildArchive(imageConfig, buildContext, "");
        buildService.buildImage(imageConfig, pullManager, buildContext, buildArchive);

        //verify that tries to pull both images
        verifyImagePull(buildConfig, pullManager, buildContext, "sample/base-image:latest");
    }

    @Test
    void testBuildImagePullsDefaultImageWhenNoFromImage() throws Exception {
        BuildImageConfiguration buildConfig = new BuildImageConfiguration.Builder()
                .build();

        buildConfig.initAndValidate(logger);

        imageConfig = new ImageConfiguration.Builder()
            .name("test")
            .buildConfig(buildConfig)
            .build();

        final ImagePullManager pullManager = new ImagePullManager(null, null, null);
        final BuildService.BuildContext buildContext = new BuildService.BuildContext.Builder()
            .mojoParameters(mojoParameters)
            .build();

        mockMavenProject();

        File buildArchive = buildService.buildArchive(imageConfig, buildContext, "");
        buildService.buildImage(imageConfig, pullManager, buildContext, buildArchive);

        verifyImagePull(buildConfig, pullManager, buildContext, DockerAssemblyManager.DEFAULT_DATA_BASE_IMAGE);
    }

    @Test
    void testDockerBuildArchiveOnly() throws Exception {
        givenAnImageConfiguration(Boolean.TRUE.toString());
        final BuildService.BuildContext buildContext = new BuildService.BuildContext.Builder()
            .mojoParameters(mojoParameters)
            .build();
        Files.createFile(tempDir.resolve("docker-build.tar"));

        File dockerArchive = buildService.buildArchive(imageConfig, buildContext, tempDir.toString());
        Assertions.assertNotNull(dockerArchive);
    }

    @Test
    void testDockerBuildArchiveOnlyWithInvalidPath() {
        givenAnImageConfiguration(Boolean.TRUE.toString());
        BuildService.BuildContext buildContext = new BuildService.BuildContext.Builder()
            .mojoParameters(mojoParameters)
            .build();
        Assertions.assertThrows(MojoExecutionException.class, () -> buildService.buildArchive(imageConfig, buildContext, "/i/donot/exist"));
    }

    @Test
    void testTagImage() throws DockerAccessException, MojoExecutionException {
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
        verifyTag();
    }

    @Test
    void tagImage_whenForceFalseAndDanglingImagesTagsPresent_thenImageNotRemoved() throws DockerAccessException, MojoExecutionException {
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
        verifyTag();
        verifyRemove(OLD_IMAGE_ID);
    }

    private void verifyTag() throws DockerAccessException {
        Mockito.verify(docker).tag(imageConfig.getName(), "quay.io/someuser/build-image:1.1.0", true);
    }

    private boolean verifyRemove(String oldimage) throws DockerAccessException {
        return Mockito.verify(docker).removeImage(Mockito.eq(oldimage), Mockito.anyBoolean());
    }

    @Test
    void tagImage_whenForceFalseAndNoDanglingTags_thenImageRemoved() throws DockerAccessException, MojoExecutionException {
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
        verifyTag();
        verifyRemove("oldimage");
    }

    @Test
    void testBuildArgsFromDifferentSources() throws MojoExecutionException, DockerAccessException {
        try {
            //takes precedence over others
            System.setProperty("docker.buildArg.http_proxy", "http://system-props.com");

            Properties mavenProjectProps = new Properties();
            mavenProjectProps.setProperty("docker.buildArg.http_proxy", "http://project-props.com");
            Mockito.doReturn(mavenProjectProps).when(mavenProject).getProperties();
            Mockito.doReturn(mavenProject).when(mojoParameters).getProject();

            BuildImageConfiguration buildConfig = new BuildImageConfiguration.Builder().build();

            imageConfig = new ImageConfiguration.Builder()
              .name("build-image")
              .alias("build-alias")
              .buildConfig(buildConfig)
              .build();

            Map<String, String> buildArgs = new HashMap<>();
            buildArgs.put("http_proxy", "http://build-context.com");

            final BuildService.BuildContext buildContext = new BuildService.BuildContext.Builder()
              .mojoParameters(mojoParameters).buildArgs(buildArgs)
              .build();

            File buildArchive = buildService.buildArchive(imageConfig, buildContext, "");
            buildService.buildImage(imageConfig, null, buildContext, buildArchive);
            Mockito.verify(docker).buildImage(Mockito.any(), Mockito.any(),
              Mockito.argThat((BuildOptions options) -> options.getOptions().get("buildargs").equals("{\"http_proxy\":\"http://system-props.com\"}")));
        } finally {
            System.clearProperty("docker.buildArg.http_proxy");
        }
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
        Mockito.doReturn(oldImageId, newImageId).when(queryService).getImageId(imageConfig.getName());
    }

    private void givenImageWithIdBeforeAndAfterTag(final String name, final String oldImageId, final String newImageId) throws DockerAccessException {
        Mockito.doReturn(oldImageId, newImageId).when(queryService).getImageId(name);
    }

    private void givenImageTags(final String name, List<String> imageTags) throws DockerAccessException {
        Mockito.doReturn(imageTags).when(docker).getImageTags(name);
    }

    private void thenImageIsBuilt() throws DockerAccessException {
        final File dockerBuildTar = tempDir.resolve("docker-build.tar").toFile();
        Mockito.verify(docker)
            .buildImage(Mockito.eq(imageConfig.getName()), Mockito.eq(dockerBuildTar), Mockito.any(BuildOptions.class));
    }

    private void thenOldImageIsNotRemoved() {
    }

    private void thenOldImageIsRemoved() throws DockerAccessException {
        Mockito.verify(docker).removeImage(oldImageId, true);
    }

    private void whenBuildImage(boolean cleanup, boolean nocache) throws DockerAccessException, MojoExecutionException {
        if (cleanup) {
            Mockito.doReturn(true).when(docker).removeImage(oldImageId, true);
        }
        final BuildService.BuildContext buildContext = new BuildService.BuildContext.Builder()
            .mojoParameters(mojoParameters)
            .build();
        File dockerArchive = buildService.buildArchive(imageConfig, buildContext, "");

        buildService.buildImage(imageConfig, params, nocache, false, Collections.emptyMap(), dockerArchive);
    }
}
