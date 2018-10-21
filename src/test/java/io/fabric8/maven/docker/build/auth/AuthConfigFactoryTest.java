package io.fabric8.maven.docker.build.auth;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.fabric8.maven.docker.util.JsonFactory;
import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.Mock;
import mockit.Mocked;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.Base64;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import static java.util.Collections.singletonMap;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author roland
 * @since 29.07.14
 */

public class AuthConfigFactoryTest {

    public static final String ECR_NAME = "123456789012.dkr.ecr.bla.amazonaws.com";

    @Mocked
    Settings settings;

    @Mocked
    private Logger log;

    private AuthConfigFactory factory;

    private boolean isPush = true;

    private Gson gson;

    public static final class MockSecDispatcher implements SecDispatcher {
        @Mock
        public String decrypt(String password) {
            return password;
        }
    }

    @Mocked
    PlexusContainer container;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void containerSetup() throws ComponentLookupException {
        final SecDispatcher secDispatcher = new MockSecDispatcher();
        new Expectations() {{
            container.lookup(SecDispatcher.ROLE, "maven"); minTimes = 0; result = secDispatcher;

        }};

        gson = new Gson();
    }

    private void setupAuthConfigFactory(boolean skipExtendedAuth, Map authConfig) {
        Map m = new HashMap();
        if (authConfig != null) {
            m.putAll(authConfig);
        }
        if (skipExtendedAuth) {
            m.put(AuthConfigFactory.SKIP_EXTENDED_AUTH, true);
        }
        factory = new AuthConfigFactory(container, settings, m, null, log);
    }

    @Test
    public void testEmpty() throws Exception {
        setupAuthConfigFactory(false, null);
        executeWithTempHomeDir(homeDir -> assertEquals(factory.createAuthConfig(isPush, null, "blubberbla:1611"), AuthConfig.EMPTY_AUTH_CONFIG));
    }


    @Test
    public void testSystemProperty() throws Exception {
        setupAuthConfigFactory(false, null);
        System.setProperty("docker.push.username","roland");
        System.setProperty("docker.push.password", "secret");
        System.setProperty("docker.push.email", "roland@jolokia.org");
        try {
            AuthConfig config = factory.createAuthConfig(true, null, null);
            verifyAuthConfig(config,"roland","secret","roland@jolokia.org");
        } finally {
            System.clearProperty("docker.push.username");
            System.clearProperty("docker.push.password");
            System.clearProperty("docker.push.email");
        }
    }


    @Test
    public void testDockerAuthLogin() throws Exception {
        setupAuthConfigFactory(false, null);

        executeWithTempHomeDir(homeDir -> {
            checkDockerAuthLogin(homeDir, "https://index.docker.io/v1/", null);
            checkDockerAuthLogin(homeDir,"localhost:5000","localhost:5000");
            checkDockerAuthLogin(homeDir,"https://localhost:5000","localhost:5000");
        });
    }

    @Test
    public void testDockerLoginNoConfig() throws MojoExecutionException, IOException {
        setupAuthConfigFactory(false, null);

        executeWithTempHomeDir(dir -> {
            AuthConfig config = factory.createAuthConfig(isPush, "roland", null);
            assertTrue(config == AuthConfig.EMPTY_AUTH_CONFIG);
        });
    }

    @Test
    public void testDockerLoginFallsBackToAuthWhenCredentialHelperDoesNotMatchDomain() throws MojoExecutionException, IOException {
        setupAuthConfigFactory(false, null);

        executeWithTempHomeDir(homeDir -> {
            writeDockerConfigJson(createDockerConfig(homeDir),null,singletonMap("registry1", "credHelper1-does-not-exist"));
            AuthConfig config = factory.createAuthConfig(isPush,"roland","localhost:5000");
            verifyAuthConfig(config,"roland","secret","roland@jolokia.org");
        });
    }

    @Test
    public void testDockerLoginNoAuthConfigFoundWhenCredentialHelperDoesNotMatchDomainOrAuth() throws MojoExecutionException, IOException {
        setupAuthConfigFactory(false, null);

        executeWithTempHomeDir(homeDir -> {
            writeDockerConfigJson(createDockerConfig(homeDir),null,singletonMap("registry1", "credHelper1-does-not-exist"));
            AuthConfig config = factory.createAuthConfig(isPush,"roland","does-not-exist-either:5000");
            assertTrue(config == AuthConfig.EMPTY_AUTH_CONFIG);
        });
    }

    @Test
    public void testDockerLoginSelectCredentialHelper() throws MojoExecutionException, IOException {
        setupAuthConfigFactory(false, null);

        executeWithTempHomeDir(homeDir -> {
            writeDockerConfigJson(createDockerConfig(homeDir),"credsStore-does-not-exist",singletonMap("registry1", "credHelper1-does-not-exist"));
            expectedException.expect(RuntimeException.class);
            expectedException.expectCause(Matchers.<Throwable>allOf(
                    instanceOf(IOException.class),
                    hasProperty("message",startsWith("Failed to start 'docker-credential-credHelper1-does-not-exist version'"))
            ));
            factory.createAuthConfig(isPush,"roland","registry1");
        });
    }

    @Test
    public void testDockerLoginSelectCredentialsStore() throws MojoExecutionException, IOException {
        setupAuthConfigFactory(false, null);

        executeWithTempHomeDir(homeDir -> {
            writeDockerConfigJson(createDockerConfig(homeDir),"credsStore-does-not-exist",singletonMap("registry1", "credHelper1-does-not-exist"));
            expectedException.expect(RuntimeException.class);
            expectedException.expectCause(Matchers.allOf(
                    instanceOf(IOException.class),
                    hasProperty("message",startsWith("Failed to start 'docker-credential-credsStore-does-not-exist version'"))
                                                        ));
            factory.createAuthConfig(isPush,"roland",null);
        });
    }

    @Test
    public void testDockerLoginDefaultToCredentialsStore() throws MojoExecutionException, IOException {
        setupAuthConfigFactory(false, null);

        executeWithTempHomeDir(homeDir -> {
            writeDockerConfigJson(createDockerConfig(homeDir),"credsStore-does-not-exist",singletonMap("registry1", "credHelper1-does-not-exist"));
            expectedException.expect(RuntimeException.class);
            expectedException.expectCause(Matchers.allOf(
                    instanceOf(IOException.class),
                    hasProperty("message",startsWith("Failed to start 'docker-credential-credsStore-does-not-exist version'"))
                                                        ));
            factory.createAuthConfig(isPush,"roland","registry2");
        });
    }

    private void executeWithTempHomeDir(HomeDirExecutor executor) throws IOException, MojoExecutionException {
        String userHome = System.getProperty("user.home");
        try {
            File tempDir = Files.createTempDirectory("d-m-p").toFile();
            System.setProperty("user.home", tempDir.getAbsolutePath());
            executor.exec(tempDir);
        } finally {
            System.setProperty("user.home",userHome);
        }

    }

    interface HomeDirExecutor {
        void exec(File dir) throws IOException, MojoExecutionException;
    }

    private void checkDockerAuthLogin(File homeDir,String configRegistry,String lookupRegistry)
            throws IOException {
        setupAuthConfigFactory(false, null);

        writeDockerConfigJson(createDockerConfig(homeDir), "roland", "secret", "roland@jolokia.org", configRegistry);
        AuthConfig config = factory.createAuthConfig(isPush, "roland", lookupRegistry);
        verifyAuthConfig(config,"roland","secret","roland@jolokia.org");
    }

    private File createDockerConfig(File homeDir) {
        File dockerDir = new File(homeDir,".docker");
        dockerDir.mkdirs();
        return dockerDir;
    }

    private void writeDockerConfigJson(File dockerDir, String user, String password,
                                       String email, String registry) throws IOException {
        File configFile = new File(dockerDir,"config.json");

        JsonObject config = new JsonObject();
        addAuths(config,user,password,email,registry);

        try (Writer writer = new FileWriter(configFile)){
            gson.toJson(config, writer);
        }
    }

    private void writeDockerConfigJson(File dockerDir,String credsStore,Map<String,String> credHelpers) throws IOException {
        File configFile = new File(dockerDir,"config.json");

        JsonObject config = new JsonObject();
        if (!credHelpers.isEmpty()){
            config.add("credHelpers", JsonFactory.newJsonObject(credHelpers));
        }

        if (credsStore!=null) {
            config.addProperty("credsStore",credsStore);
        }

        addAuths(config,"roland","secret","roland@jolokia.org", "localhost:5000");

        try (Writer writer = new FileWriter(configFile)){
            gson.toJson(config, writer);
        }
    }

    private void addAuths(JsonObject config,String user,String password,String email,String registry) {
        JsonObject auths = new JsonObject();
        JsonObject value = new JsonObject();
        value.addProperty("auth", new String(Base64.encodeBase64((user + ":" + password).getBytes())));
        value.addProperty("email",email);
        auths.add(registry, value);
        config.add("auths",auths);
    }

    @Test
    public void testOpenShiftConfigFromPluginConfig() throws Exception {
        Map<String,String> authConfigMap = new HashMap<>();
        authConfigMap.put("useOpenShiftAuth","true");
        setupAuthConfigFactory(false, authConfigMap);

        executeWithTempHomeDir(homeDir -> {
            createOpenShiftConfig(homeDir,"openshift_simple_config.yaml");
            AuthConfig config = factory.createAuthConfig(isPush,  "roland", null);
            verifyAuthConfig(config,"admin","token123",null);
        });
    }

    @Test
    public void testOpenShiftConfigFromSystemProps() throws Exception {
        setupAuthConfigFactory(false, null);

        try {
            System.setProperty("docker.useOpenShiftAuth","true");
            executeWithTempHomeDir(homeDir -> {
                createOpenShiftConfig(homeDir, "openshift_simple_config.yaml");
                AuthConfig config = factory.createAuthConfig(isPush, "roland", null);
                verifyAuthConfig(config, "admin", "token123", null);
            });
        } finally {
            System.getProperties().remove("docker.useOpenShiftAuth");
        }
    }

    @Test
    public void testOpenShiftConfigFromSystemPropsNegative() throws Exception {
        Map<String,String> authConfigMap = new HashMap<>();
        authConfigMap.put("useOpenShiftAuth","true");
        setupAuthConfigFactory(false, authConfigMap);

        try {
            System.setProperty("docker.useOpenShiftAuth","false");
            executeWithTempHomeDir(homeDir -> {
                createOpenShiftConfig(homeDir, "openshift_simple_config.yaml");
                AuthConfig config = factory.createAuthConfig(isPush,  "roland", null);
                assertTrue(config == AuthConfig.EMPTY_AUTH_CONFIG);
            });
        } finally {
            System.getProperties().remove("docker.useOpenShiftAuth");
        }
    }

    @Test
    public void testOpenShiftConfigNotLoggedIn() throws Exception {
        Map<String,String> authConfigMap = new HashMap<>();
        authConfigMap.put("useOpenShiftAuth","true");
        setupAuthConfigFactory(false, authConfigMap);

        executeWithTempHomeDir(homeDir -> {
            createOpenShiftConfig(homeDir,"openshift_nologin_config.yaml");
            expectedException.expect(IllegalArgumentException.class);
            expectedException.expectMessage(containsString("~/.kube/config"));
            factory.createAuthConfig(isPush,"roland",null);
        });

    }

    private void createOpenShiftConfig(File homeDir,String testConfig) throws IOException {
        File kubeDir = new File(homeDir,".kube");
        kubeDir.mkdirs();
        File config = new File(kubeDir,"config");
        IOUtil.copy(getClass().getResourceAsStream(testConfig),new FileWriter(config));
    }

    @Test
    public void testSystemPropertyNoPassword() throws IOException {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("No docker.password provided for username secret");
        checkException("docker.username");
    }

    private void checkException(String key) throws IOException {
        setupAuthConfigFactory(false, null);

        System.setProperty(key, "secret");
        try {
            factory.createAuthConfig(isPush,  null, null);
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    public void testFromPluginConfiguration() throws IOException {

        Map pluginConfig = new HashMap();
        pluginConfig.put("username", "roland");
        pluginConfig.put("password", "secret");
        pluginConfig.put("email", "roland@jolokia.org");

        setupAuthConfigFactory(false, pluginConfig);

        AuthConfig config = factory.createAuthConfig(isPush, null, null);
        verifyAuthConfig(config, "roland", "secret", "roland@jolokia.org");
    }

    @Test
    public void testFromPluginConfigurationPull() throws IOException {
        Map pullConfig = new HashMap();
        pullConfig.put("username", "roland");
        pullConfig.put("password", "secret");
        pullConfig.put("email", "roland@jolokia.org");
        Map pluginConfig = new HashMap();
        pluginConfig.put("pull",pullConfig);
        setupAuthConfigFactory(false, pluginConfig);

        AuthConfig config = factory.createAuthConfig(false,  null, null);
        verifyAuthConfig(config, "roland", "secret", "roland@jolokia.org");
    }


    @Test
    public void testFromPluginConfigurationFailed() throws IOException {
        Map pluginConfig = new HashMap();
        pluginConfig.put("username","admin");
        setupAuthConfigFactory(false, pluginConfig);

        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("No 'password' given while using <authConfig> in configuration for mode DEFAULT");
        factory.createAuthConfig(isPush,null,null);
    }

    @Test
    public void testFromSettingsSimple() throws IOException {
        setupServers();
        setupAuthConfigFactory(false, null);

        AuthConfig config = factory.createAuthConfig(isPush, "roland", "test.org");
        assertNotNull(config);
        verifyAuthConfig(config, "roland", "secret", "roland@jolokia.org");
    }

    @Test
    public void testFromSettingsDefault() throws IOException {
        setupServers();
        setupAuthConfigFactory(false, null);

        AuthConfig config = factory.createAuthConfig(isPush, "fabric8io", "test.org");
        assertNotNull(config);
        verifyAuthConfig(config, "fabric8io", "secret2", "fabric8io@redhat.com");
    }

    @Test
    public void testFromSettingsDefault2() throws IOException {
        setupServers();
        setupAuthConfigFactory(false, null);

        AuthConfig config = factory.createAuthConfig(isPush, "tanja", null);
        assertNotNull(config);
        verifyAuthConfig(config,"tanja","doublesecret","tanja@jolokia.org");
    }

    @Test
    public void testWrongUserName() throws IOException, MojoExecutionException {
        setupAuthConfigFactory(false, null);

        executeWithTempHomeDir(homeDir -> {
            setupServers();
            assertEquals(factory.createAuthConfig(isPush,"roland","another.repo.org"), AuthConfig.EMPTY_AUTH_CONFIG);
        });
    }


    private void setupServers() {
        new Expectations() {{
            List<Server> servers = new ArrayList<>();

            servers.add(create(ECR_NAME, "roland", "secret", "roland@jolokia.org"));
            servers.add(create("test.org", "fabric8io", "secret2", "fabric8io@redhat.com"));
            servers.add(create("test.org/roland", "roland", "secret", "roland@jolokia.org"));
            servers.add(create("docker.io", "tanja", "doublesecret", "tanja@jolokia.org"));
            servers.add(create("another.repo.org/joe", "joe", "3secret", "joe@foobar.com"));
            settings.getServers();
            result = servers;
        }

            private Server create(String id, String user, String password, String email) {
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
        };
    }

    private void verifyAuthConfig(AuthConfig config, String username, String password, String email) {
        JsonObject params = gson.fromJson(new String(Base64.decodeBase64(config.toHeaderValue().getBytes())), JsonObject.class);
        assertEquals(username,params.get("username").getAsString());
        assertEquals(password,params.get("password").getAsString());
        if (email != null) {
            assertEquals(email, params.get("email").getAsString());
        }
    }

}
