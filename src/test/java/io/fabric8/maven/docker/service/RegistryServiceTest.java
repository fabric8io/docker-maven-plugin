package io.fabric8.maven.docker.service;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.CreateImageOptions;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.BuildXConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.ImagePullPolicy;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import io.fabric8.maven.docker.util.AutoPullMode;
import io.fabric8.maven.docker.util.DockerFileUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.ProjectPaths;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;

/**
 * @author roland
 * @since 23.11.17
 */
@ExtendWith(MockitoExtension.class)
class RegistryServiceTest {

    private Exception actualException;
    private String registry;
    private ImageConfiguration imageConfiguration;

    // pull
    private String imageName;
    private ImagePullPolicy imagePullPolicy;
    private TestCacheStore cacheStore;
    private AutoPullMode autoPullMode;
    private RegistryService registryService;
    private boolean hasImage;
    private Map<String, String> authConfig;
    private MockedStatic<DockerFileUtil> dockerFileUtilMockedStatic;
    private MockedConstruction<BuildXService.DockerVersionExternalCommand> dockerVersionExternalCommandMockedConstruction;

    @TempDir
    private File projectBaseDir;

    @Mock
    private DockerAccess docker;

    @Mock
    private DockerAssemblyManager dockerAssemblyManager;

    @Mock
    private Logger logger;

    @Mock
    private AuthConfigFactory authConfigFactory;

    @Mock
    private QueryService queryService;

    @Mock
    private BuildXService.Exec exec;

    private static String getOsDependentBuild(Path buildPath, String docker) {
        return buildPath.resolve(docker).toString().replace('/', File.separatorChar);
    }

    @BeforeEach
    void setup() {
        BuildXService buildXService = new BuildXService(docker, dockerAssemblyManager, logger, exec);
        registryService = new RegistryService(docker, queryService, buildXService, logger);
        cacheStore = new TestCacheStore();
        authConfig = new HashMap<>();

        imageName = null;
        imagePullPolicy = null;
        autoPullMode = null;
        hasImage = false;
        registry = null;
        imageConfiguration = null;
        dockerFileUtilMockedStatic = mockStatic(DockerFileUtil.class);
        dockerVersionExternalCommandMockedConstruction = mockConstruction(BuildXService.DockerVersionExternalCommand.class);
    }

    @AfterEach
    void tearDown() {
        dockerFileUtilMockedStatic.close();
        dockerVersionExternalCommandMockedConstruction.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void pullImagePullPolicyAlways(boolean hasImage) throws Exception {
        givenAnImage();
        givenAnImageConfiguration("myregistry.com/user/app:1.0.1");
        givenImagePullPolicy(ImagePullPolicy.Always);
        givenHasImage(hasImage);

        checkPulledButNotTagged();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void pullImageAutopullAlways(boolean hasImage) throws Exception {
        givenAnImage();
        givenAnImageConfiguration("myregistry.com/user/app:1.0.1");
        givenAutoPullMode(AutoPullMode.ALWAYS);
        givenHasImage(hasImage);

        checkPulledButNotTagged();
    }

    private void checkPulledButNotTagged() throws DockerAccessException {

        whenAutoPullImage();

        thenImageHasBeenPulled();
        thenImageHasNotBeenTagged();
        thenNoExceptionThrown();
    }

    @Test
    void pullImageAlwaysWhenPreviouslyPulled() throws Exception {
        givenAnImage();
        givenAnImageConfiguration("myregistry.com/user/app:1.0.1");
        givenHasImage(false);
        givenPreviousPulled(true);
        givenImagePullPolicy(ImagePullPolicy.Always);

        checkNotPulledAndNotTagged();
    }

    private void checkNotPulledAndNotTagged() throws DockerAccessException {
        whenAutoPullImage();

        thenImageHasNotBeenPulled();
        thenImageHasNotBeenTagged();
        thenNoExceptionThrown();
    }

    @Test
    void alreadyPulled() throws DockerAccessException {
        givenAnImage();
        givenAnImageConfiguration("myregistry.com/user/app:1.0.1");
        givenPreviousPulled(true);

        whenAutoPullImage();

        thenImageHasNotBeenPulled();
        thenImageHasNotBeenTagged();
        thenNoExceptionThrown();
    }

    @Test
    void policyNeverWithImageAvailable() throws DockerAccessException {
        givenAnImage();
        givenHasImage(true);
        givenPreviousPulled(false);
        givenImagePullPolicy(ImagePullPolicy.Never);

        whenAutoPullImage();

        thenImageHasNotBeenPulled();
        thenImageHasNotBeenTagged();

    }

    @Test
    void policyNeverWithImageNotAvailable() throws DockerAccessException {
        givenAnImage();
        givenHasImage(false);
        givenAnImageConfiguration("myregistry.com/user/app:1.0.1");
        givenPreviousPulled(false);
        givenImagePullPolicy(ImagePullPolicy.Never);

        whenAutoPullImage();

        thenImageHasNotBeenPulled();
        thenImageHasNotBeenTagged();
        thenExceptionThrown();
    }

    private void thenExceptionThrown() {
        Assertions.assertNotNull(actualException);
        Assertions.assertTrue(actualException.getMessage().contains(imageName));
    }

    @Test
    void pullWithCustomRegistry() throws DockerAccessException {
        givenAnImageConfiguration("myregistry.com/user/app:1.0.1");
        givenAnImage("myregistry.com/user/test:1.0.1");
        givenHasImage(false);
        givenPreviousPulled(false);
        givenRegistry("anotherRegistry.com");
        givenImagePullPolicy(ImagePullPolicy.IfNotPresent);

        whenAutoPullImage();

        thenImageHasBeenPulledWithRegistry("myregistry.com");
        thenImageHasNotBeenTagged();
        thenNoExceptionThrown();
    }

    @Test
    void pullImageWithPolicy_pullPolicyAlwaysAndBuildConfiguration_shouldPull() throws DockerAccessException {
        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder()
            .createImageOptions(Collections.singletonMap("platform", "linux/amd64"))
            .build();
        imageConfiguration = new ImageConfiguration.Builder()
            .name("myregistry.com/user/app:1.0.1")
            .buildConfig(buildImageConfiguration).build();
        givenAnImage("myregistry.com/user/test:1.0.1");
        givenHasImage(false);
        givenPreviousPulled(false);
        givenRegistry("anotherRegistry.com");
        givenImagePullPolicy(ImagePullPolicy.Always);

        whenAutoPullImage();

        ArgumentCaptor<String> pulledImage = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<CreateImageOptions> imageCapture = ArgumentCaptor.forClass(CreateImageOptions.class);
        Mockito.verify(docker).pullImage(pulledImage.capture(), Mockito.any(), Mockito.anyString(), imageCapture.capture(), Mockito.anyInt());

        Assertions.assertEquals("myregistry.com/user/test:1.0.1", pulledImage.getValue());
        CreateImageOptions createImageOptions = imageCapture.getValue();

        Assertions.assertNotNull(createImageOptions);
        Assertions.assertEquals(3, createImageOptions.getOptions().size());
        Assertions.assertEquals("linux/amd64", createImageOptions.getOptions().get("platform"));
        Assertions.assertEquals("1.0.1", createImageOptions.getOptions().get("tag"));
        Assertions.assertEquals("myregistry.com/user/test", createImageOptions.getOptions().get("fromImage"));
    }

    @Test
    void tagForCustomRegistry() throws DockerAccessException {
        givenAnImageConfiguration("myregistry.com/user/app:1.0.1");
        givenAnImage("user/test:1.0.1");
        givenHasImage(false);
        givenPreviousPulled(false);
        givenRegistry("anotherRegistry.com");
        givenImagePullPolicy(ImagePullPolicy.IfNotPresent);

        whenAutoPullImage();

        thenImageHasBeenPulledWithRegistry("anotherRegistry.com");
        thenImageHasBeenTagged();
        thenNoExceptionThrown();
    }

    @Test
    void pushImage() throws DockerAccessException {
        givenAnImageConfiguration("user/test:1.0.1");

        whenPushImage();

        thenImageHasBeenPushed();
        thenNoExceptionThrown();
    }

    @Test
    void pushImageWithRegistry() throws DockerAccessException {
        String registry = "myregistry.com";
        givenAnImageConfiguration("user/test:1.0.1");
        givenRegistry(registry);

        whenPushImage();

        thenImageHasBeenPushed();
        thenRegistryWasUsedWhenPushing(registry);
        thenNoExceptionThrown();
    }

    @Test
    void pushImageWithImageRegistry() throws DockerAccessException {
        String registry = "myregistry.com";
        givenAnImageConfiguration(registry + "/" + "user/test:1.0.1");

        whenPushImage();

        thenImageHasBeenPushed();
        thenRegistryWasUsedWhenPushing(registry);
        thenNoExceptionThrown();
    }

    @Test
    void pushImageWithImageConfigRegistry() throws DockerAccessException {
        String registry = "myregistry.com";
        givenAnImageConfiguration("user/test:1.0.1");
        andImageConfigRegistry(registry);

        whenPushImage();

        thenImageHasBeenPushed();
        thenRegistryWasUsedWhenPushing(registry);
        thenNoExceptionThrown();
    }

    @Test
    void pushBuildXImage() throws MojoExecutionException {
        givenBuildxImageConfiguration("user/test:1.0.1", null, null, null);
        givenCredentials("skroob", "12345");
        givenRegistry(registry);
        givenRegistryCredentialsPresentInLocalDockerConfig();

        whenPushImage();

        thenBuildxImageHasBeenPushed(null, null, false, null);
        thenNoExceptionThrown();
    }

    @Test
    void pushBuildXImageWithDockerfile() throws MojoExecutionException {
        String dockerFile = projectBaseDir.toPath().resolve("src/docker/Dockerfile").toString();
        givenBuildxImageConfiguration("user/test:1.0.1", null, dockerFile, null);
        givenCredentials("skroob", "12345");
        givenRegistry(registry);
        givenRegistryCredentialsPresentInLocalDockerConfig();

        whenPushImage();

        thenBuildxImageHasBeenPushed(null, "Dockerfile", false, null);
        thenNoExceptionThrown();
    }

    @Test
    void pushBuildXImageProvidedBuilder() throws MojoExecutionException {
        givenBuildxImageConfiguration("user/test:1.0.1", "provided-builder", null, null);
        givenCredentials("King_Roland_of_Druidia", "12345");
        givenRegistry(registry);
        givenRegistryCredentialsPresentInLocalDockerConfig();

        whenPushImage();

        thenBuildxImageHasBeenPushed("provided-builder", null, false, null);
        thenNoExceptionThrown();
    }

    @Test
    void pushBuildXImageTag() throws MojoExecutionException {
        givenBuildxImageConfiguration("user/test:1.0.1", null, null, "perri-air");
        givenCredentials("King_Roland_of_Druidia", "12345");
        givenRegistry(registry);
        givenRegistryCredentialsPresentInLocalDockerConfig();

        whenPushImage();

        thenBuildxImageHasBeenPushed(null, null, true, null);
        thenNoExceptionThrown();
    }

    @Test
    void pushBuildXImageWithRegistry() throws MojoExecutionException {
        String registry = "myregistry.com";
        givenBuildxImageConfiguration("user/test:1.0.1", null, null, "perri-air");
        givenCredentials("King_Roland_of_Druidia", "12345");
        givenRegistry(registry);
        givenRegistryCredentialsPresentInLocalDockerConfig();

        whenPushImage();

        thenBuildxImageHasBeenPushed(null, null, true, registry);
        thenNoExceptionThrown();
    }

    @Test
    void pushBuildXImageWithImageRegistry() throws MojoExecutionException {
        String registry = "myregistry.com";
        givenBuildxImageConfiguration(registry + "/" + "user/test:1.0.1", null, null, "perri-air");
        givenCredentials("King_Roland_of_Druidia", "12345");
        givenRegistry(registry);
        givenRegistryCredentialsPresentInLocalDockerConfig();

        whenPushImage();

        thenBuildxImageHasBeenPushed(null, null, true, registry);
        thenNoExceptionThrown();
    }

    @Test
    void pushBuildXImageWithImageConfigRegistry() throws MojoExecutionException {
        String registry = "myregistry.com";
        givenBuildxImageConfiguration("user/test:1.0.1", null, null, "perri-air");
        andImageConfigRegistry(registry);
        givenCredentials("King_Roland_of_Druidia", "12345");
        givenRegistry(registry);
        givenRegistryCredentialsPresentInLocalDockerConfig();

        whenPushImage();

        thenBuildxImageHasBeenPushed(null, null, true, registry);
        thenNoExceptionThrown();
    }

    @Test
    void pushImageWithoutBuildConfig() throws DockerAccessException {
        givenAnImageConfigurationWithoutBuildConfig("user/test:1.0.1");

        whenPushImage();

        thenImageHasNotBeenPushed();
        thenNoExceptionThrown();
    }

    @Test
    void pushImageSkipped() throws DockerAccessException {
        givenAnImageConfiguration("user/test:1.0.1");
        givenPushSkipped(true);

        whenPushImage();

        thenImageHasNotBeenPushed();
        thenNoExceptionThrown();
    }

    // ====================================================================================================

    private void thenNoExceptionThrown() {
        Assertions.assertNull(actualException);
    }

    private void thenImageHasNotBeenPulled() throws DockerAccessException {
        Mockito.verify(docker, Mockito.never()).pullImage(Mockito.anyString(), Mockito.any(AuthConfig.class), Mockito.anyString(), Mockito.any(CreateImageOptions.class), Mockito.anyInt());
    }

    private void thenImageHasNotBeenPushed() throws DockerAccessException {
        Mockito.verify(docker, Mockito.never()).pushImage(Mockito.anyString(), Mockito.any(AuthConfig.class), Mockito.anyString(), Mockito.anyInt());
    }

    private void thenImageHasBeenPushed() throws DockerAccessException {
        Mockito.verify(docker).pushImage(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.anyInt());
    }
    private void thenRegistryWasUsedWhenPushing(String registry) throws DockerAccessException {
        Mockito.verify(docker).pushImage(Mockito.anyString(), Mockito.any(), Mockito.eq(registry), Mockito.anyInt());
    }

    private void thenBuildxImageHasBeenPushed(String providedBuilder, String relativeDockerfile, boolean tag, String registry) throws MojoExecutionException {

        Path buildPath = projectBaseDir.toPath().resolve("target/docker").resolve(imageConfiguration.getName().replace(":","/"));
        String config = getOsDependentBuild(buildPath, "docker");
        String buildDir = getOsDependentBuild(buildPath, "build");
        String builderName = providedBuilder != null ? providedBuilder : "maven";

        if (providedBuilder == null) {
            Mockito.verify(exec).process(Arrays.asList("docker", "buildx", "create", "--driver", "docker-container", "--name", builderName, "--node",  builderName+"0"));
        }

        List<String> cmds =
            BuildXService.append(new ArrayList<>(), "docker", "buildx", "build",
                "--progress=plain", "--builder", builderName, "--platform",
                "linux/amd64,linux/arm64", "--tag",
                new ImageName(imageConfiguration.getName()).getFullName(registry));
        if (tag) {
            String tagName = imageConfiguration.getBuildConfiguration().getTags().get(0);
            BuildXService.append(cmds, "--tag",
                new ImageName(imageConfiguration.getName(), tagName).getFullName(registry));
        }

        if (relativeDockerfile != null) {
            Path dockerBuild = buildPath.resolve("tmp/docker-build");
            BuildXService.append(cmds, "--file=" + dockerBuild.resolve(relativeDockerfile), dockerBuild.toString());
        } else {
            cmds.add(buildDir);
        }
        BuildXService.append(cmds, "--push");

        Mockito.verify(exec).process(cmds);
    }

    private void thenImageHasBeenTagged() throws DockerAccessException {
        Mockito.verify(docker).tag(new ImageName(imageName).getFullName(registry), imageName, false);
    }

    private void thenImageHasNotBeenTagged() throws DockerAccessException {
        Mockito.verify(docker, Mockito.never()).tag(Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean());
    }

    private void thenImageHasBeenPulled() throws DockerAccessException {
        thenImageHasBeenPulledWithRegistry(null);
    }

    private void thenImageHasBeenPulledWithRegistry(final String registry) throws DockerAccessException {
        Mockito.verify(docker).pullImage(Mockito.eq(imageName), Mockito.any(), Mockito.eq(registry), Mockito.any(CreateImageOptions.class), Mockito.anyInt());
        Assertions.assertNotNull(cacheStore.get(imageName));
    }

    private void whenAutoPullImage() {

        try {
            String iPolicyS = imagePullPolicy != null ? imagePullPolicy.toString() : null;
            String autoPullModeS = autoPullMode != null ? autoPullMode.toString() : null;
            ImagePullManager pullManager = new ImagePullManager(cacheStore, iPolicyS, autoPullModeS);
            RegistryService.RegistryConfig.Builder registryConfigBuilder =
                new RegistryService.RegistryConfig.Builder()
                    .authConfigFactory(authConfigFactory)
                    .authConfig(authConfig);
            if (registry != null) {
                registryConfigBuilder.registry(registry);
            }
            registryService.pullImageWithPolicy(imageName, pullManager, registryConfigBuilder.build(), imageConfiguration.getBuildConfiguration(), 0);

        } catch (Exception e) {
            this.actualException = e;
        }
    }

    private void whenPushImage() {
        try {
            ProjectPaths projectPaths = new ProjectPaths(projectBaseDir, "target/docker");

            RegistryService.RegistryConfig registryConfig =
                new RegistryService.RegistryConfig.Builder()
                    .authConfigFactory(authConfigFactory)
                    .authConfig(authConfig)
                    .registry(registry)
                    .build();
            registryService.pushImages(projectPaths, Collections.singleton(imageConfiguration), 1, registryConfig, false);
        } catch (Exception e) {
            this.actualException = e;
        }
    }

    private void givenImagePullPolicy(ImagePullPolicy policy) {
        this.imagePullPolicy = policy;
    }

    private void givenAutoPullMode(AutoPullMode autoPullMode) {
        this.autoPullMode = autoPullMode;
    }

    private void givenHasImage(boolean hasImage) {
        this.hasImage = hasImage;
    }

    private void givenPreviousPulled(boolean pulled) {
        this.cacheStore.put("dummyKey", pulled ? "{ '" + imageName + "': true}" : null);
    }

    private void givenAnImage() {
        givenAnImage("test:latest");
    }

    private void givenRegistry(String registry) {
        this.registry = registry;
    }

    private void givenAnImage(String imageName) {
        this.imageName = imageName;
    }

    private void givenAnImageConfiguration(String imageName) {
        givenImageNameAndBuildX(imageName, null, null, null);
    }

    private void givenBuildxImageConfiguration(String imageName, String builderName, String dockerFile, String tag) {
        BuildXConfiguration buildx = new BuildXConfiguration.Builder()
            .platforms(Arrays.asList("linux/amd64", "linux/arm64"))
            .builderName(builderName)
            .nodeName("maven0")
            .build();
        givenImageNameAndBuildX(imageName, buildx, dockerFile, tag);
    }

    private void givenImageNameAndBuildX(String imageName, BuildXConfiguration buildx, String dockerFile, String tag) {
        List<String> tags = tag != null ? Collections.singletonList(tag) : null;
        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder().buildx(buildx).tags(tags).dockerFile(dockerFile).build();
        buildImageConfiguration.initAndValidate(logger);
        imageConfiguration = new ImageConfiguration.Builder().name(imageName).buildConfig(buildImageConfiguration).build();
    }

    private void andImageConfigRegistry(String registry) {
        Objects.requireNonNull(imageConfiguration, "ImageConfiguration must first be set with `givenAnImage....`");
        imageConfiguration.setRegistry(registry);
    }

    private void givenRegistryCredentialsPresentInLocalDockerConfig() {
        AuthConfig testAuth = new AuthConfig(authConfig);
        testAuth.setRegistry(registry);

        dockerFileUtilMockedStatic.when(DockerFileUtil::readDockerConfig)
            .thenReturn(testAuth.toJsonObject());
    }

    private void givenCredentials(String username, String password) throws MojoExecutionException {
        authConfig.put(AuthConfig.AUTH_USERNAME, username);
        authConfig.put(AuthConfig.AUTH_PASSWORD, password);
        Mockito.doReturn(new AuthConfig(authConfig))
            .when(authConfigFactory)
            .createAuthConfig(Mockito.eq(true), Mockito.eq(false), Mockito.eq(authConfig), Mockito.any(), Mockito.eq("user"), Mockito.any());
    }

    private void givenAnImageConfigurationWithoutBuildConfig(String imageName) {
        imageConfiguration = new ImageConfiguration.Builder().name(imageName).buildConfig(null).build();
    }

    private void givenPushSkipped(boolean skipPush) {
        final BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder().skipPush(skipPush).build();
        imageConfiguration = new ImageConfiguration.Builder(imageConfiguration).buildConfig(buildImageConfiguration).build();
    }

    private static class TestCacheStore implements ImagePullManager.CacheStore {

        String cache;

        @Override
        public String get(String key) {
            return cache;
        }

        @Override
        public void put(String key, String value) {
            cache = value;
        }
    }
}
