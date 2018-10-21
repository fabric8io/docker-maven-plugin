package io.fabric8.maven.docker.build;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.build.auth.AuthConfig;
import io.fabric8.maven.docker.build.auth.AuthConfigFactory;
import io.fabric8.maven.docker.build.docker.DockerRegistryService;
import io.fabric8.maven.docker.build.docker.ImagePullCache;
import io.fabric8.maven.docker.build.maven.MavenRegistryContext;
import io.fabric8.maven.docker.config.ImageName;
import io.fabric8.maven.docker.config.build.ImagePullPolicy;
import io.fabric8.maven.docker.util.AutoPullMode;
import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author roland
 * @since 23.11.17
 */
public class DockerRegistryServiceTest {

    private Exception actualException;

    private String imageName;
    private ImagePullPolicy imagePullPolicy;
    private TestBackend cacheStore;
    private AutoPullMode autoPullMode;
    private DockerRegistryService registryService;
    private boolean hasImage;
    private String registry;
    private Map authConfig;

    @Mocked
    private DockerAccess docker;

    @Mocked
    private Logger logger;

    @Mocked
    private AuthConfigFactory authConfigFactory;

    @Before
    public void setup() {
        reset();
    }

    private void reset() {
        cacheStore = new TestBackend();
        registryService = new DockerRegistryService(docker, logger, cacheStore);
        authConfig = new HashMap();

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
            givenAutoPullMode(AutoPullMode.ALWAYS);
            givenHasImage(hasImage);

            checkPulledButNotTagged();
        }
    }

    private void checkPulledButNotTagged() throws IOException {
        givenEmptyAuthConfig();

        whenAutoPullImage();

        thenImageHasBeenPulled();
        thenImageHasNotBeenTagged();
        thenNoExceptionThrown();
    }

    @Test
    public void pullImageAlwaysWhenPreviouslyPulled() throws Exception {
        givenAnImage();
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
    public void pullWithCustomRegistry() throws IOException {
        givenAnImage("myregistry.com/user/test:1.0.1");
        givenHasImage(false);
        givenPreviousPulled(false);
        givenRegistry("anotherRegistry.com");
        givenImagePullPolicy(ImagePullPolicy.IfNotPresent);
        givenEmptyAuthConfig();

        whenAutoPullImage();

        thenImageHasBeenPulledWithRegistry("myregistry.com");
        thenImageHasNotBeenTagged();
        thenNoExceptionThrown();
    }

    @Test
    public void tagForCustomRegistry() throws IOException {
        givenAnImage("user/test:1.0.1");
        givenHasImage(false);
        givenPreviousPulled(false);
        givenRegistry("anotherRegistry.com");
        givenImagePullPolicy(ImagePullPolicy.IfNotPresent);
        givenEmptyAuthConfig();

        whenAutoPullImage();

        thenImageHasBeenPulledWithRegistry("anotherRegistry.com");
        thenImageHasBeenTagged();
        thenNoExceptionThrown();
    }



    // ====================================================================================================

    private void thenNoExceptionThrown() {
        assertNull(actualException);
    }
    private void thenImageHasNotBeenPulled() throws DockerAccessException {
        new Verifications() {{
            docker.pullImage(anyString, withNotNull(), anyString);times = 0;
        }};
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
            docker.pullImage(imageName, withNotNull(), registry);
        }};
        assertTrue(cacheStore.get(imageName) != null);
    }

    private void whenAutoPullImage() {


        try {
            MavenRegistryContext buildContext =
                new MavenRegistryContext.Builder()
                    .authConfigFactory(authConfigFactory)
                    .pullRegistry(registry)
                    .build();
            registryService.pullImage(imageName, imagePullPolicy, buildContext);
        } catch (Exception e) {
            //e.printStackTrace();
            this.actualException = e;
        }
    }

    private void givenEmptyAuthConfig() throws IOException {
        new Expectations() {{
            authConfigFactory.createAuthConfig(anyBoolean, anyString, anyString);
            result = AuthConfig.EMPTY_AUTH_CONFIG;
        }};
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

    private void givenAuthConfig(Map authConfig) {
        this.authConfig = authConfig;
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

    private class TestBackend implements ImagePullCache.Backend {

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
