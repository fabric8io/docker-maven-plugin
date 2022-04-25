package io.fabric8.maven.docker.service;

import io.fabric8.maven.docker.access.CreateImageOptions;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.BuildXConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImagePullPolicy;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import io.fabric8.maven.docker.util.AutoPullMode;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import mockit.VerificationsInOrder;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Settings;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author roland
 * @since 23.11.17
 */
public class RegistryServiceTest {

    private Exception actualException;

    // pull
    private String imageName;
    private ImagePullPolicy imagePullPolicy;
    private TestCacheStore cacheStore;
    private AutoPullMode autoPullMode;
    private RegistryService registryService;
    private boolean hasImage;
    private String registry;
    private Map<String, String> authConfig;

    private Path tempPath = Paths.get(System.getProperty("java.io.tmpdir"));

    // push
    private ImageConfiguration imageConfiguration;

    @Mocked
    private DockerAccess docker;

    @Mocked
    private Logger logger;

    @Mocked
    private AuthConfigFactory authConfigFactory;

    @Mocked
    private QueryService queryService;

    @Mocked
    private BuildXService.Exec exec;

    @Before
    public void setup() {
        reset();
    }

    private void reset() {
        BuildXService buildXService = new BuildXService(docker, logger, exec);
        registryService = new RegistryService(docker, queryService, buildXService, logger);
        cacheStore = new TestCacheStore();
        authConfig = new HashMap<>();

        imageName = null;
        imagePullPolicy = null;
        autoPullMode = null;
        hasImage = false;
        registry = null;
    }

    @Test
    public void pullImagePullPolicyAlways() throws Exception {
        for (boolean hasImage : new boolean[]{ false, true }) {
            reset();
            givenAnImage();
            givenAnImageConfiguration("myregistry.com/user/app:1.0.1");
            givenImagePullPolicy(ImagePullPolicy.Always);
            givenHasImage(hasImage);

            checkPulledButNotTagged();
        }
    }

    @Test
    public void pullImageAutopullAlways() throws Exception {
        for (boolean hasImage : new boolean[]{ false, true }) {
            reset();
            givenAnImage();
            givenAnImageConfiguration("myregistry.com/user/app:1.0.1");
            givenAutoPullMode(AutoPullMode.ALWAYS);
            givenHasImage(hasImage);

            checkPulledButNotTagged();
        }
    }

    private void checkPulledButNotTagged() throws DockerAccessException {

        whenAutoPullImage();

        thenImageHasBeenPulled();
        thenImageHasNotBeenTagged();
        thenNoExceptionThrown();
    }

    @Test
    public void pullImageAlwaysWhenPreviouslyPulled() throws Exception {
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
    public void alreadyPulled() throws DockerAccessException {
        givenAnImage();
        givenAnImageConfiguration("myregistry.com/user/app:1.0.1");
        givenPreviousPulled(true);

        whenAutoPullImage();

        thenImageHasNotBeenPulled();
        thenImageHasNotBeenTagged();
        thenNoExceptionThrown();
    }

    @Test
    public void policyNeverWithImageAvailable() throws DockerAccessException {
        givenAnImage();
        givenHasImage(true);
        givenPreviousPulled(false);
        givenImagePullPolicy(ImagePullPolicy.Never);

        whenAutoPullImage();

        thenImageHasNotBeenPulled();
        thenImageHasNotBeenTagged();

    }

    @Test
    public void policyNeverWithImageNotAvailable() throws DockerAccessException {
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
        assertNotNull(actualException);
        assertTrue(actualException.getMessage().contains(imageName));
    }

    @Test
    public void pullWithCustomRegistry() throws DockerAccessException {
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
    public void pullImageWithPolicy_pullPolicyAlwaysAndBuildConfiguration_shouldPull() throws DockerAccessException {
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

        new Verifications() {{
            String pulledImage;
            CreateImageOptions createImageOptions;
            docker.pullImage(pulledImage = withCapture(), (AuthConfig) any, anyString, createImageOptions = withCapture());
            times = 1;

            assertEquals("myregistry.com/user/test:1.0.1", pulledImage);
            assertNotNull(createImageOptions);
            assertEquals(3, createImageOptions.getOptions().size());
            assertEquals("linux/amd64", createImageOptions.getOptions().get("platform"));
            assertEquals("1.0.1", createImageOptions.getOptions().get("tag"));
            assertEquals("myregistry.com/user/test", createImageOptions.getOptions().get("fromImage"));
        }};
    }

    @Test
    public void tagForCustomRegistry() throws DockerAccessException {
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
    public void pushImage() throws DockerAccessException {
        givenAnImageConfiguration("user/test:1.0.1");

        whenPushImage();

        thenImageHasBeenPushed();
        thenNoExceptionThrown();
    }

    @Test
    public void pushBuildXImage() throws MojoExecutionException {
        givenBuildxImageConfiguration("user/test:1.0.1");
        givenCredentials("skroob", "12345");

        whenPushImage();

        thenBuildxImageHasBeenPushed();
        thenNoExceptionThrown();
    }

    @Test
    public void pushImageWithoutBuildConfig() throws DockerAccessException {
        givenAnImageConfigurationWithoutBuildConfig("user/test:1.0.1");

        whenPushImage();

        thenImageHasNotBeenPushed();
        thenNoExceptionThrown();
    }

    @Test
    public void pushImageSkipped() throws DockerAccessException {
        givenAnImageConfiguration("user/test:1.0.1");
        givenPushSkipped(true);

        whenPushImage();

        thenImageHasNotBeenPushed();
        thenNoExceptionThrown();
    }

    // ====================================================================================================

    private void thenNoExceptionThrown() {
        assertNull(actualException);
    }
    private void thenImageHasNotBeenPulled() throws DockerAccessException {
        new Verifications() {{
            docker.pullImage(anyString, withNotNull(), anyString, (CreateImageOptions) any); times = 0;
        }};
    }

    private void thenImageHasNotBeenPushed() throws DockerAccessException {
        new Verifications() {{
            docker.pushImage(anyString, withNotNull(), anyString, anyInt); times = 0;
        }};
    }

    private void thenImageHasBeenPushed() throws DockerAccessException {
        new Verifications() {{
            docker.pushImage(anyString, withNotNull(), anyString, anyInt);
        }};
    }

    private void thenBuildxImageHasBeenPushed() throws MojoExecutionException {
        Path buildPath = tempPath.resolve("user/test/1.0.1");
        String config = getOsDependentBuild(buildPath, "docker");
        String cacheDir = getOsDependentBuild(buildPath, "cache");
        String buildDir = getOsDependentBuild(buildPath, "build");
        String builderName = "dmp_user_test_1.0.1";

        new VerificationsInOrder() {{
            exec.process(Arrays.asList("docker", "--config", config, "buildx"),
                "create", "--driver", "docker-container", "--name", builderName);

            exec.process(Arrays.asList("docker", "--config", config, "buildx",
                "build", "--progress=plain", "--builder", builderName,
                "--platform", "linux/amd64,linux/arm64",
                "--tag", "user/test:1.0.1", "--push",
                "--cache-to=type=local,dest=" + cacheDir, "--cache-from=type=local,src=" + cacheDir,
                buildDir));

            exec.process(Arrays.asList("docker", "--config", config, "buildx"),
                "rm", builderName);
        }};
    }

    private static String getOsDependentBuild(Path buildPath, String docker) {
        return buildPath.resolve(docker).toString().replace('/', File.separatorChar);
    }

    private void thenImageHasBeenTagged() throws DockerAccessException {
        new Verifications() {{
            docker.tag(new ImageName(imageName).getFullName(registry), imageName, false);
        }};
    }


    private void thenImageHasNotBeenTagged() throws DockerAccessException {
        new Verifications() {{
            docker.tag(anyString, anyString, anyBoolean); times = 0;
        }};
    }

    private void thenImageHasBeenPulled() throws DockerAccessException {
        thenImageHasBeenPulledWithRegistry(null);
    }

    private void thenImageHasBeenPulledWithRegistry(final String registry) throws DockerAccessException {
        new Verifications() {{
            docker.pullImage(imageName, withNotNull(), registry, (CreateImageOptions) any);
        }};
        assertNotNull(cacheStore.get(imageName));
    }

    private void whenAutoPullImage() {

        try {
            String iPolicyS = imagePullPolicy != null ? imagePullPolicy.toString() : null;
            String autoPullModeS = autoPullMode != null ? autoPullMode.toString() : null;
            ImagePullManager pullManager = new ImagePullManager(cacheStore,iPolicyS, autoPullModeS);
            RegistryService.RegistryConfig.Builder registryConfigBuilder =
                new RegistryService.RegistryConfig.Builder()
                .authConfigFactory(authConfigFactory)
                .authConfig(authConfig);
            if (registry != null) {
                registryConfigBuilder.registry(registry);
            }
            registryService.pullImageWithPolicy(imageName, pullManager, registryConfigBuilder.build(), imageConfiguration.getBuildConfiguration());

        } catch (Exception e) {
            this.actualException = e;
        }
    }

    private void whenPushImage() {
        try {
            RegistryService.RegistryConfig registryConfig =
                    new RegistryService.RegistryConfig.Builder()
                            .authConfigFactory(authConfigFactory)
                            .authConfig(authConfig).build();
            registryService.pushImages(tempPath, Collections.singleton(imageConfiguration), 1, registryConfig, false);
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
        givenImageNameAndBuildX(imageName, null);
    }

    private void givenBuildxImageConfiguration(String imageName) {
        BuildXConfiguration buildx = new BuildXConfiguration.Builder().platforms(Arrays.asList("linux/amd64", "linux/arm64")).build();
        givenImageNameAndBuildX(imageName, buildx);
    }

    private void givenImageNameAndBuildX(String imageName, BuildXConfiguration buildx) {
        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder().buildx(buildx).build();
        imageConfiguration = new ImageConfiguration.Builder().name(imageName).buildConfig(buildImageConfiguration).build();
    }

    private void givenCredentials(String username, String password) throws MojoExecutionException {
        authConfig.put(AuthConfig.AUTH_USERNAME, username);
        authConfig.put(AuthConfig.AUTH_PASSWORD, password);

        new Expectations() {{
            authConfigFactory.createAuthConfig(true, false, authConfig, (Settings) any, "user", null);
            result = new AuthConfig(authConfig);
        }};
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
