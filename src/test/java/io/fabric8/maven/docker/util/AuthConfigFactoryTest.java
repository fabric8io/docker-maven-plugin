package io.fabric8.maven.docker.util;

import com.google.common.collect.ImmutableMap;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.AuthConfigList;
import io.fabric8.maven.docker.access.util.ExternalCommand;
import io.fabric8.maven.docker.util.aws.AwsSdkAuthConfigFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.DefaultSettingsDecrypter;
import org.codehaus.plexus.util.Base64;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariableMocker;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonMap;
import static java.util.UUID.randomUUID;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * @author roland
 * @since 29.07.14
 */
@ExtendWith(MockitoExtension.class)
@ExtendWith(SystemStubsExtension.class)
class AuthConfigFactoryTest {

    public static final String ECR_NAME = "123456789012.dkr.ecr.bla.amazonaws.com";

    @SystemStub
    private EnvironmentVariables environmentVariables;

    @SystemStub
    private SystemProperties systemProperties;

    @Mock
    Settings settings;

    @Mock
    private Logger log;

    @Mock
    private AwsSdkAuthConfigFactory awsSdkAuthConfigFactory;

    private AuthConfigFactory factory;

    private boolean isPush = true;

    private GsonBuilder gsonBuilder;

    private HttpServer httpServer;

    public static final class MockSecDispatcher implements SecDispatcher {
        public String decrypt(String password) {
            return password;
        }
    }

    @Mock
    SecDispatcher secDispatcher;

    @BeforeEach
    void containerSetup() throws SecDispatcherException {
        Mockito.lenient().when(secDispatcher.decrypt(Mockito.anyString())).thenAnswer(invocation -> invocation.getArguments()[0]);

        factory = new AuthConfigFactory(new DefaultSettingsDecrypter(secDispatcher));
        factory.setLog(log);

        gsonBuilder = new GsonBuilder();
    }

    @AfterEach
    void shutdownHttpServer() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
    }

    @Test
    void testEmpty() throws Exception {
        executeWithTempHomeDir(homeDir -> Assertions.assertNull(factory.createAuthConfig(isPush, false, null, settings, null, "blubberbla:1611")));
    }

    @Test
    void testSystemProperty() throws Exception {
        systemProperties
            .set("docker.push.username", "roland")
            .set("docker.push.password", "secret")
            .set("docker.push.email", "roland@jolokia.org");
        AuthConfig config = factory.createAuthConfig(true, false, null, settings, null, null);
        verifyAuthConfig(config, "roland", "secret", "roland@jolokia.org");
    }

    private void testSystemProperty(String prefix) throws Exception {
        systemProperties
            .set(prefix + ".username", "roland")
            .set(prefix + ".password", "secret")
            .set(prefix + ".email", "roland@jolokia.org");
        AuthConfig config = factory.createAuthConfig(true, false, null, settings, null, null);
        verifyAuthConfig(config, "roland", "secret", "roland@jolokia.org");
    }

    @Test
    void testDockerSystemProperty() throws Exception {
        testSystemProperty("docker");
    }

    @Test
    void testRegistrySystemProperty() throws Exception {
        testSystemProperty("registry");
    }

    @Test
    void testDockerSystemPropertyHasPrecedence() throws Exception {
        systemProperties
            .set("docker.username", "roland")
            .set("docker.password", "secret")
            .set("docker.email", "roland@jolokia.org")
            .set("registry.username", "_roland")
            .set("registry.password", "_secret1")
            .set("registry.email", "_1roland@jolokia.org");
        AuthConfig config = factory.createAuthConfig(true, false, null, settings, null, null);
        verifyAuthConfig(config, "roland", "secret", "roland@jolokia.org");
    }

    @Test
    void testDockerAuthLogin() throws Exception {
        executeWithTempHomeDir(homeDir -> {
            checkDockerAuthLogin(homeDir, AuthConfigFactory.DOCKER_LOGIN_DEFAULT_REGISTRY, null);
            checkDockerAuthLogin(homeDir, "localhost:5000", "localhost:5000");
            checkDockerAuthLogin(homeDir, "https://localhost:5000", "localhost:5000");
        });
    }

    @Test
    void testDockerLoginNoConfig() throws MojoExecutionException, IOException {
        executeWithTempHomeDir(dir -> {
            AuthConfig config = factory.createAuthConfig(isPush, false, null, settings, "roland", null);
            Assertions.assertNull(config);
        });
    }

    @Test
    void testDockerLoginFallsBackToAuthWhenCredentialHelperDoesNotMatchDomain() throws MojoExecutionException, IOException {
        executeWithTempHomeDir(homeDir -> {
            writeDockerConfigJson(createDockerConfig(homeDir), null, singletonMap("registry1", "credHelper1-does-not-exist"));
            AuthConfig config = factory.createAuthConfig(isPush, false, null, settings, "roland", "localhost:5000");
            verifyAuthConfig(config, "roland", "secret", "roland@jolokia.org");
        });
    }

    @Test
    void testDockerLoginNoAuthConfigFoundWhenCredentialHelperDoesNotMatchDomainOrAuth() throws MojoExecutionException, IOException {
        executeWithTempHomeDir(homeDir -> {
            writeDockerConfigJson(createDockerConfig(homeDir), null, singletonMap("registry1", "credHelper1-does-not-exist"));
            AuthConfig config = factory.createAuthConfig(isPush, false, null, settings, "roland", "does-not-exist-either:5000");
            Assertions.assertNull(config);
        });
    }

    @Test
    void testDockerLoginNoErrorWhenEmailInAuthConfigContainsNullValue() throws MojoExecutionException, IOException {
        executeWithTempHomeDir(homeDir -> {
            writeDockerConfigJson(createDockerConfig(homeDir), "roland", "pass", null, "registry:5000", true);
            AuthConfig config = factory.createAuthConfig(isPush, false, null, settings, "roland", "registry:5000");
            Assertions.assertNotNull(config);
        });
    }

    @Test
    void testDockerLoginSelectCredentialHelper() throws IOException {
        try {
            executeProcessWithTempHomeDir(homeDir -> {
                writeDockerConfigJson(createDockerConfig(homeDir), "credsStore-does-not-exist", singletonMap("registry1", "credHelper1-does-not-exist"));
                factory.createAuthConfig(isPush, false, null, settings, "roland", "registry1");
            });
            Assertions.fail("MojoExecutionException not thrown");
        } catch (MojoExecutionException expectedException) {
            Throwable cause = expectedException.getCause();
            assertTrue(cause instanceof IOException);
            assertTrue(cause.getMessage().startsWith("Failed to start 'docker-credential-credHelper1-does-not-exist get'"));
        }
    }

    @Test
    void testDockerLoginSelectCredentialsStore() throws IOException {
        try {
            executeProcessWithTempHomeDir(homeDir -> {
                writeDockerConfigJson(createDockerConfig(homeDir), "credsStore-does-not-exist", singletonMap("registry1", "credHelper1-does-not-exist"));
                factory.createAuthConfig(isPush, false, null, settings, "roland", null);
            });
            Assertions.fail("MojoExecutionException not thrown");
        } catch (MojoExecutionException expectedException) {
            Throwable cause = expectedException.getCause();
            assertTrue(cause instanceof IOException);
            assertTrue(cause.getMessage().startsWith("Failed to start 'docker-credential-credsStore-does-not-exist get'"));
        }
    }

    @Test
    void testDockerLoginDefaultToCredentialsStore() throws IOException {
        try {
            executeProcessWithTempHomeDir(homeDir -> {
                writeDockerConfigJson(createDockerConfig(homeDir), "credsStore-does-not-exist", singletonMap("registry1", "credHelper1-does-not-exist"));
                factory.createAuthConfig(isPush, false, null, settings, "roland", "registry2");
            });
            Assertions.fail("MojoExecutionException not thrown");
        } catch (MojoExecutionException expectedException) {
            Throwable cause = expectedException.getCause();
            assertTrue(cause instanceof IOException);
            assertTrue(cause.getMessage().startsWith("Failed to start 'docker-credential-credsStore-does-not-exist get'"));
        }
    }

    @Test
    void hasAuthForRegistryInDockerConfig_whenRegistryAbsentInDockerConfig_thenReturnFalse() throws Exception {
        try (MockedStatic<DockerFileUtil> dockerFileUtilMockedStatic = mockStatic(DockerFileUtil.class)) {
            // Given
            AuthConfig authConfig = new AuthConfig("testuser", "testpassword", null, null, null);
            authConfig.setRegistry("unknown-registry.io");
            dockerFileUtilMockedStatic.when(DockerFileUtil::readDockerConfig)
                .thenReturn(authConfig.toJsonObject());

            // When
            boolean result = AuthConfigFactory.hasAuthForRegistryInDockerConfig(log, "configured-registry.io", new AuthConfigList());

            // Then
            assertFalse(result);
        }
    }

    @Test
    void hasAuthForRegistryInDockerConfig_whenRegistryInDockerConfigButDifferentUser_thenReturnFalse() throws Exception {
        try (MockedStatic<DockerFileUtil> dockerFileUtilMockedStatic = mockStatic(DockerFileUtil.class)) {
            // Given
            AuthConfig authConfigInDockerConfig = new AuthConfig("user", "secret", null, null, null);
            authConfigInDockerConfig.setRegistry("configured-registry.io");
            dockerFileUtilMockedStatic.when(DockerFileUtil::readDockerConfig)
                .thenReturn(authConfigInDockerConfig.toJsonObject());
            AuthConfig authConfig = new AuthConfig("testuser", "testpassword", null, null, null);
            authConfig.setRegistry("configured-registry.io");

            // When
            boolean result = AuthConfigFactory.hasAuthForRegistryInDockerConfig(log, "configured-registry.io", new AuthConfigList(authConfig));

            // Then
            assertFalse(result);
        }
    }

    @Test
    void hasAuthForRegistryInDockerConfig_whenRegistryInDockerConfig_thenReturnTrue() throws Exception {
        try (MockedStatic<DockerFileUtil> dockerFileUtilMockedStatic = mockStatic(DockerFileUtil.class)) {
            // Given
            AuthConfig authConfig = new AuthConfig("testuser", "testpassword", null, null, null);
            authConfig.setRegistry("configured-registry.io");
            dockerFileUtilMockedStatic.when(DockerFileUtil::readDockerConfig)
                .thenReturn(authConfig.toJsonObject());

            // When
            boolean result = AuthConfigFactory.hasAuthForRegistryInDockerConfig(log, "configured-registry.io", new AuthConfigList(authConfig));

            // Then
            assertTrue(result);
        }
    }

    @Test
    void hasAuthForRegistryInDockerConfig_whenDefaultDockerRegistryInDockerConfig_thenReturnTrue() throws Exception {
        try (MockedStatic<DockerFileUtil> dockerFileUtilMockedStatic = mockStatic(DockerFileUtil.class)) {
            // Given
            AuthConfig authConfig = new AuthConfig("testuser", "testpassword", null, null, null);
            authConfig.setRegistry("https://index.docker.io/v1/");
            dockerFileUtilMockedStatic.when(DockerFileUtil::readDockerConfig)
                .thenReturn(authConfig.toJsonObject());

            // When
            boolean result = AuthConfigFactory.hasAuthForRegistryInDockerConfig(log, "docker.io", new AuthConfigList(authConfig));

            // Then
            assertTrue(result);
        }
    }

    interface HomeDirExecutor {
        void exec(File dir) throws IOException, MojoExecutionException;
    }

    interface ModifiedEnvironmentExecutor {
        void exec(HomeDirExecutor outer, Map<String, String> envVariables) throws IOException, MojoExecutionException;
    }

    private void executeProcessWithTempHomeDir(HomeDirExecutor executor) throws IOException, MojoExecutionException {
        try (MockedStatic<ExternalCommand> mocked = mockStatic(ExternalCommand.class)) {
            String[] cmd = Mockito.any();
            mocked.when(() -> ExternalCommand.startCommand(cmd)).thenThrow(new IOException());
            executeWithTempHomeDir(executor);
        }
    }

    private void executeWithTempHomeDir(HomeDirExecutor executor) throws IOException, MojoExecutionException {
        executeWithEnvironment(executor, this::executeWithHome);
        executeWithEnvironment(executor, this::executeWithUserHome);
    }

    private void executeWithEnvironment(HomeDirExecutor executor, ModifiedEnvironmentExecutor modifiedEnvironmentExecutor) throws IOException, MojoExecutionException {
        Map<String, String> envVariables = new HashMap<>();
        try {
            EnvironmentVariableMocker.connect(envVariables);
            envVariables.remove("KUBECONFIG");
            modifiedEnvironmentExecutor.exec(executor, envVariables);
        } finally {
            EnvironmentVariableMocker.remove(envVariables);
        }
    }

    private void executeWithHome(HomeDirExecutor executor, Map<String, String> envVariables) throws IOException, MojoExecutionException {
        // execute with HOME environment variable (preferred)
        File tempDir = Files.createTempDirectory("d-m-p").toFile();
        envVariables.put("HOME", tempDir.getAbsolutePath());
        systemProperties.set("user.home", "/dev/null/ignore/me");

        executor.exec(tempDir);
    }

    private void executeWithUserHome(HomeDirExecutor executor, Map<String, String> envVariables) throws IOException, MojoExecutionException {
        // execute with user.home system property (fallback)
        File tempDir = Files.createTempDirectory("d-m-p").toFile();
        envVariables.remove("HOME");
        systemProperties.set("user.home", tempDir.getAbsolutePath());

        executor.exec(tempDir);
    }

    private void checkDockerAuthLogin(File homeDir, String configRegistry, String lookupRegistry)
        throws IOException, MojoExecutionException {
        writeDockerConfigJson(createDockerConfig(homeDir), "roland", "secret", "roland@jolokia.org", configRegistry, false);
        AuthConfig config = factory.createAuthConfig(isPush, false, null, settings, "roland", lookupRegistry);
        verifyAuthConfig(config, "roland", "secret", "roland@jolokia.org");
    }

    private File createDockerConfig(File homeDir) {
        File dockerDir = new File(homeDir, ".docker");
        dockerDir.mkdirs();
        return dockerDir;
    }

    private void writeDockerConfigJson(File dockerDir, String user, String password,
        String email, String registry, boolean shouldSerializeNulls) throws IOException {
        File configFile = new File(dockerDir, "config.json");

        JsonObject config = new JsonObject();
        addAuths(config, user, password, email, registry);

        try (Writer writer = new FileWriter(configFile)) {
            if (shouldSerializeNulls) {
                gsonBuilder.serializeNulls();
            }
            gsonBuilder.create().toJson(config, writer);
        }
    }

    private void writeDockerConfigJson(File dockerDir, String credsStore, Map<String, String> credHelpers) throws IOException {
        File configFile = new File(dockerDir, "config.json");

        JsonObject config = new JsonObject();
        if (!credHelpers.isEmpty()) {
            config.add("credHelpers", JsonFactory.newJsonObject(credHelpers));
        }

        if (credsStore != null) {
            config.addProperty("credsStore", credsStore);
        }

        addAuths(config, "roland", "secret", "roland@jolokia.org", "localhost:5000");

        try (Writer writer = new FileWriter(configFile)) {
            gsonBuilder.create().toJson(config, writer);
        }
    }

    private void addAuths(JsonObject config, String user, String password, String email, String registry) {
        JsonObject auths = new JsonObject();
        JsonObject value = new JsonObject();
        value.addProperty("auth", new String(Base64.encodeBase64((user + ":" + password).getBytes())));
        if (email == null) {
            value.add("email", JsonNull.INSTANCE);
        } else {
            value.addProperty("email", email);
        }

        auths.add(registry, value);
        config.add("auths", auths);
    }

    @Test
    void testOpenShiftConfigFromPluginConfig() throws Exception {
        executeWithTempHomeDir(homeDir -> {
            createOpenShiftConfig(homeDir, "openshift_simple_config.yaml");
            Map<String, String> authConfigMap = new HashMap<>();
            authConfigMap.put("useOpenShiftAuth", "true");
            AuthConfig config = factory.createAuthConfig(isPush, false, authConfigMap, settings, "roland", null);
            verifyAuthConfig(config, "admin", "token123", null);
        });
    }

    @Test
    void testOpenShiftConfigFromSystemProps() throws Exception {
        systemProperties.set("docker.useOpenShiftAuth", "true");
        executeWithTempHomeDir(homeDir -> {
            createOpenShiftConfig(homeDir, "openshift_simple_config.yaml");
            AuthConfig config = factory.createAuthConfig(isPush, false, null, settings, "roland", null);
            verifyAuthConfig(config, "admin", "token123", null);
        });
    }

    @Test
    void testOpenShiftConfigFromSystemPropsNegative() throws Exception {
        systemProperties.set("docker.useOpenShiftAuth", "false");
        executeWithTempHomeDir(homeDir -> {
            createOpenShiftConfig(homeDir, "openshift_simple_config.yaml");
            Map<String, String> authConfigMap = new HashMap<>();
            authConfigMap.put("useOpenShiftAuth", "true");
            AuthConfig config = factory.createAuthConfig(isPush, false, authConfigMap, settings, "roland", null);
            Assertions.assertNull(config);
        });
    }

    @Test
    void testOpenShiftConfigNotLoggedIn() throws Exception {
        try {
            executeWithTempHomeDir(homeDir -> {
                createOpenShiftConfig(homeDir, "openshift_nologin_config.yaml");
                Map<String, String> authConfigMap = new HashMap<>();
                authConfigMap.put("useOpenShiftAuth", "true");
                factory.createAuthConfig(isPush, false, authConfigMap, settings, "roland", null);
            });

            Assertions.fail("MojoExecutionException not thrown");
        } catch (MojoExecutionException expectedException) {
            assertTrue(expectedException.getMessage().contains(".kube/config"));
        }
    }

    private void createOpenShiftConfig(File homeDir, String testConfig) throws IOException {
        File kubeDir = new File(homeDir, ".kube");
        kubeDir.mkdirs();
        File config = new File(kubeDir, "config");
        IOUtil.copy(getClass().getResourceAsStream(testConfig), new FileWriter(config));
    }

    @Test
    void testSystemPropertyNoPassword() {
        MojoExecutionException mee = Assertions.assertThrows(MojoExecutionException.class, () -> checkException("docker.username"));
        Assertions.assertEquals("No docker.password provided for username secret", mee.getMessage());
    }

    private void checkException(String key) throws MojoExecutionException {
        systemProperties.set(key, "secret");
        factory.createAuthConfig(isPush, false, null, settings, null, null);
    }

    @Test
    void testFromPluginConfiguration() throws MojoExecutionException {
        Map pluginConfig = new HashMap();
        pluginConfig.put("username", "roland");
        pluginConfig.put("password", "secret");
        pluginConfig.put("email", "roland@jolokia.org");

        AuthConfig config = factory.createAuthConfig(isPush, false, pluginConfig, settings, null, null);
        verifyAuthConfig(config, "roland", "secret", "roland@jolokia.org");
    }

    @Test
    void testFromPluginConfigurationPull() throws MojoExecutionException {
        Map pullConfig = new HashMap();
        pullConfig.put("username", "roland");
        pullConfig.put("password", "secret");
        pullConfig.put("email", "roland@jolokia.org");
        Map pluginConfig = new HashMap();
        pluginConfig.put("pull", pullConfig);
        AuthConfig config = factory.createAuthConfig(false, false, pluginConfig, settings, null, null);
        verifyAuthConfig(config, "roland", "secret", "roland@jolokia.org");
    }

    @Test
    void testFromPluginConfigurationFailed() {
        Map pluginConfig = new HashMap();
        pluginConfig.put("username", "admin");
        try {
            factory.createAuthConfig(isPush, false, pluginConfig, settings, null, null);
            Assertions.fail("MojoExecutionException not thrown");
        } catch (MojoExecutionException expectedException) {
            Assertions.assertEquals("No 'password' given while using <authConfig> in configuration for mode DEFAULT", expectedException.getMessage());
        }
    }

    @Test
    void testFromSettingsSimple() throws MojoExecutionException {
        setupServers();
        AuthConfig config = factory.createAuthConfig(isPush, false, null, settings, "roland", "test.org");
        Assertions.assertNotNull(config);
        verifyAuthConfig(config, "roland", "secret", "roland@jolokia.org");
    }

    @Test
    void testFromSettingsDefault() throws MojoExecutionException {
        setupServers();
        AuthConfig config = factory.createAuthConfig(isPush, false, null, settings, "fabric8io", "test.org");
        Assertions.assertNotNull(config);
        verifyAuthConfig(config, "fabric8io", "secret2", "fabric8io@redhat.com");
    }

    @Test
    void testFromSettingsDefault2() throws MojoExecutionException {
        setupServers();
        AuthConfig config = factory.createAuthConfig(isPush, false, null, settings, "tanja", null);
        Assertions.assertNotNull(config);
        verifyAuthConfig(config, "tanja", "doublesecret", "tanja@jolokia.org");
    }

    @Test
    void testWrongUserName() throws IOException, MojoExecutionException {
        executeWithTempHomeDir(homeDir -> {
            setupServers();
            Assertions.assertNull(factory.createAuthConfig(isPush, false, null, settings, "roland", "another.repo.org"));
        });
    }

    @Test
    void getAuthConfigViaAwsSdk() throws MojoExecutionException {
        String accessKeyId = randomUUID().toString();
        String secretAccessKey = randomUUID().toString();
        environmentVariables
            .set("AWS_ACCESS_KEY_ID", accessKeyId)
            .set("AWS_SECRET_ACCESS_KEY", secretAccessKey);

        AuthConfig authConfig = factory.createAuthConfig(false, true, null, settings, "user", ECR_NAME);

        verifyAuthConfig(authConfig, accessKeyId, secretAccessKey, null, null);
    }

    @Test
    void ecsTaskRole() throws IOException, MojoExecutionException {
        String containerCredentialsUri = "/v2/credentials/" + randomUUID();
        String accessKeyId = randomUUID().toString();
        String secretAccessKey = randomUUID().toString();
        String sessionToken = randomUUID().toString();
        givenEcsMetadataService(containerCredentialsUri, accessKeyId, secretAccessKey, sessionToken);
        setupEcsMetadataConfiguration(httpServer, containerCredentialsUri);

        AuthConfig authConfig = factory.createAuthConfig(false, true, null, settings, "user", ECR_NAME);

        verifyAuthConfig(authConfig, accessKeyId, secretAccessKey, null, sessionToken);
    }

    @Test
    void fargateTaskRole() throws IOException, MojoExecutionException {
        String containerCredentialsUri = "v2/credentials/" + randomUUID();
        String accessKeyId = randomUUID().toString();
        String secretAccessKey = randomUUID().toString();
        String sessionToken = randomUUID().toString();
        givenEcsMetadataService("/" + containerCredentialsUri, accessKeyId, secretAccessKey, sessionToken);
        setupEcsMetadataConfiguration(httpServer, containerCredentialsUri);

        AuthConfig authConfig = factory.createAuthConfig(false, true, null, settings, "user", ECR_NAME);

        verifyAuthConfig(authConfig, accessKeyId, secretAccessKey, null, sessionToken);
    }

    @Test
    void awsTemporaryCredentialsArePickedUpFromEnvironment() throws MojoExecutionException {
        String accessKeyId = randomUUID().toString();
        String secretAccessKey = randomUUID().toString();
        String sessionToken = randomUUID().toString();
        environmentVariables
            .set("AWS_ACCESS_KEY_ID", accessKeyId)
            .set("AWS_SECRET_ACCESS_KEY", secretAccessKey)
            .set("AWS_SESSION_TOKEN", sessionToken);

        AuthConfig authConfig = factory.createAuthConfig(false, true, null, settings, "user", ECR_NAME);

        verifyAuthConfig(authConfig, accessKeyId, secretAccessKey, null, sessionToken);
    }

    @Test
    void awsStaticCredentialsArePickedUpFromEnvironment() throws MojoExecutionException {
        String accessKeyId = randomUUID().toString();
        String secretAccessKey = randomUUID().toString();
        environmentVariables
            .set("AWS_ACCESS_KEY_ID", accessKeyId)
            .set("AWS_SECRET_ACCESS_KEY", secretAccessKey);

        AuthConfig authConfig = factory.createAuthConfig(false, true, null, settings, "user", ECR_NAME);

        verifyAuthConfig(authConfig, accessKeyId, secretAccessKey, null, null);
    }

    @Test
    void incompleteAwsCredentialsAreIgnored() throws MojoExecutionException, IOException {
        executeProcessWithTempHomeDir(homeDir -> {
            environmentVariables.set("AWS_ACCESS_KEY_ID", randomUUID().toString());
            Assertions.assertNull(factory.createAuthConfig(false, true, null, settings, "user", ECR_NAME));
        });
    }

    private static Server create(String id, String user, String password, String email) {
        Server server = new Server();
        server.setId(id);
        server.setUsername(user);
        server.setPassword(password);
        Xpp3Dom dom = new Xpp3Dom("configuration");
        Xpp3Dom emailD = new Xpp3Dom("email");
        emailD.setValue(email);
        dom.addChild(emailD);
        server.setConfiguration(dom);
        return server;
    }

    private void setupServers() {
        List<Server> servers = new ArrayList<>();
        servers.add(create(ECR_NAME, "roland", "secret", "roland@jolokia.org"));
        servers.add(create("test.org", "fabric8io", "secret2", "fabric8io@redhat.com"));
        servers.add(create("test.org/roland", "roland", "secret", "roland@jolokia.org"));
        servers.add(create("docker.io", "tanja", "doublesecret", "tanja@jolokia.org"));
        servers.add(create("another.repo.org/joe", "joe", "3secret", "joe@foobar.com"));

        Mockito.when(settings.getServers()).thenReturn(servers);
    }

    private void givenEcsMetadataService(String containerCredentialsUri, String accessKeyId, String secretAccessKey, String sessionToken) throws IOException {
        httpServer =
            ServerBootstrap.bootstrap()
                .setLocalAddress(InetAddress.getLoopbackAddress())
                .registerHandler("*", (request, response, context) -> {
                    System.out.println("REQUEST: " + request.getRequestLine());
                    if (containerCredentialsUri.matches(request.getRequestLine().getUri())) {
                        response.setEntity(new StringEntity(gsonBuilder.create().toJson(ImmutableMap.of(
                            "AccessKeyId", accessKeyId,
                            "SecretAccessKey", secretAccessKey,
                            "Token", sessionToken
                        ))));
                    } else {
                        response.setStatusCode(SC_NOT_FOUND);
                    }
                })
                .create();
        httpServer.start();
    }

    private void setupEcsMetadataConfiguration(HttpServer httpServer, String containerCredentialsUri) {
        environmentVariables
            .set("ECS_METADATA_ENDPOINT", "http://" + httpServer.getInetAddress().getHostAddress() + ":" + httpServer.getLocalPort())
            .set("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI", containerCredentialsUri);
    }

    private void verifyAuthConfig(AuthConfig config, String username, String password, String email, String auth) {
        Assertions.assertNotNull(config);
        JsonObject params = gsonBuilder.create().fromJson(new String(Base64.decodeBase64(config.toHeaderValue().getBytes())), JsonObject.class);
        Assertions.assertEquals(username, params.get(AuthConfig.AUTH_USERNAME).getAsString());
        Assertions.assertEquals(password, params.get(AuthConfig.AUTH_PASSWORD).getAsString());
        if (email != null) {
            Assertions.assertEquals(email, params.get(AuthConfig.AUTH_EMAIL).getAsString());
        }
        if (auth != null) {
            Assertions.assertEquals(auth, params.get(AuthConfig.AUTH_AUTH).getAsString());
        }
    }

    private void verifyAuthConfig(AuthConfig config, String username, String password, String email) {
        verifyAuthConfig(config, username, password, email, null);
    }
}

