package io.fabric8.maven.docker.util;

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
import io.fabric8.maven.docker.access.AuthConfig;
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
        factory = new AuthConfigFactory(container);
        factory.setLog(log);

        gson = new Gson();
    }

    @Test
    public void testEmpty() throws Exception {
        executeWithTempHomeDir(new HomeDirExecutor() {
            @Override
            public void exec(File homeDir) throws IOException, MojoExecutionException {
                assertNull(factory.createAuthConfig(isPush,false,null,settings,null,"blubberbla:1611"));
            }
        });
    }

    @Test
    public void testSystemProperty() throws Exception {
        System.setProperty("docker.push.username","roland");
        System.setProperty("docker.push.password", "secret");
        System.setProperty("docker.push.email", "roland@jolokia.org");
        try {
            AuthConfig config = factory.createAuthConfig(true, false, null, settings, null, null);
            verifyAuthConfig(config,"roland","secret","roland@jolokia.org");
        } finally {
            System.clearProperty("docker.push.username");
            System.clearProperty("docker.push.password");
            System.clearProperty("docker.push.email");
        }
    }

    private void testSystemProperty(String prefix) throws Exception {
        System.setProperty(prefix + ".username", "roland");
        System.setProperty(prefix + ".password", "secret");
        System.setProperty(prefix + ".email", "roland@jolokia.org");
        try {
            AuthConfig config = factory.createAuthConfig(true, false, null, settings, null, null);
            verifyAuthConfig(config, "roland", "secret", "roland@jolokia.org");
        } finally {
            System.clearProperty(prefix + ".username");
            System.clearProperty(prefix + ".password");
            System.clearProperty(prefix + ".email");
        }
    }

    @Test
    public void testDockerSystemProperty() throws Exception {
        testSystemProperty("docker");
    }

    @Test
    public void testRegistrySystemProperty() throws Exception {
        testSystemProperty("registry");
    }

    @Test
    public void testDockerSystemPropertyHasPrecedence() throws Exception {
        System.setProperty("docker.username", "roland");
        System.setProperty("docker.password", "secret");
        System.setProperty("docker.email", "roland@jolokia.org");
        System.setProperty("registry.username", "_roland");
        System.setProperty("registry.password", "_secret1");
        System.setProperty("registry.email", "_1roland@jolokia.org");
        try {
            AuthConfig config = factory.createAuthConfig(true, false, null, settings, null, null);
            verifyAuthConfig(config, "roland", "secret", "roland@jolokia.org");
        } finally {
            System.clearProperty("docker.username");
            System.clearProperty("docker.password");
            System.clearProperty("docker.email");
            System.clearProperty("registry.username");
            System.clearProperty("registry.password");
            System.clearProperty("registry.email");
        }
    }

    @Test
    public void testDockerAuthLogin() throws Exception {
        executeWithTempHomeDir(new HomeDirExecutor() {
            @Override
            public void exec(File homeDir) throws IOException, MojoExecutionException {
                checkDockerAuthLogin(homeDir,AuthConfigFactory.DOCKER_LOGIN_DEFAULT_REGISTRY,null);
                checkDockerAuthLogin(homeDir,"localhost:5000","localhost:5000");
                checkDockerAuthLogin(homeDir,"https://localhost:5000","localhost:5000");
            }
        });
    }

    @Test
    public void testDockerLoginNoConfig() throws MojoExecutionException, IOException {
        executeWithTempHomeDir(new HomeDirExecutor() {
            @Override
            public void exec(File dir) throws IOException, MojoExecutionException {
                AuthConfig config = factory.createAuthConfig(isPush, false, null, settings, "roland", null);
                assertNull(config);
            }
        });
    }

    @Test
    public void testDockerLoginFallsBackToAuthWhenCredentialHelperDoesNotMatchDomain() throws MojoExecutionException, IOException {
        executeWithTempHomeDir(new HomeDirExecutor() {
            @Override
            public void exec(File homeDir) throws IOException, MojoExecutionException {
                writeDockerConfigJson(createDockerConfig(homeDir),null,singletonMap("registry1", "credHelper1-does-not-exist"));
                AuthConfig config = factory.createAuthConfig(isPush,false,null,settings,"roland","localhost:5000");
                verifyAuthConfig(config,"roland","secret","roland@jolokia.org");
            }
        });
    }

    @Test
    public void testDockerLoginNoAuthConfigFoundWhenCredentialHelperDoesNotMatchDomainOrAuth() throws MojoExecutionException, IOException {
        executeWithTempHomeDir(new HomeDirExecutor() {
            @Override
            public void exec(File homeDir) throws IOException, MojoExecutionException {
                writeDockerConfigJson(createDockerConfig(homeDir),null,singletonMap("registry1", "credHelper1-does-not-exist"));
                AuthConfig config = factory.createAuthConfig(isPush,false,null,settings,"roland","does-not-exist-either:5000");
                assertNull(config);
            }
        });
    }

    @Test
    public void testDockerLoginSelectCredentialHelper() throws MojoExecutionException, IOException {
        executeWithTempHomeDir(new HomeDirExecutor() {
            @Override
            public void exec(File homeDir) throws IOException, MojoExecutionException {
                writeDockerConfigJson(createDockerConfig(homeDir),"credsStore-does-not-exist",singletonMap("registry1", "credHelper1-does-not-exist"));
                expectedException.expect(MojoExecutionException.class);
                expectedException.expectCause(Matchers.allOf(
                        instanceOf(IOException.class),
                        hasProperty("message",startsWith("Failed to start 'docker-credential-credHelper1-does-not-exist get'"))
                ));
                factory.createAuthConfig(isPush,false,null,settings,"roland","registry1");
            }
        });
    }

    @Test
    public void testDockerLoginSelectCredentialsStore() throws MojoExecutionException, IOException {
        executeWithTempHomeDir(new HomeDirExecutor() {
            @Override
            public void exec(File homeDir) throws IOException, MojoExecutionException {
                writeDockerConfigJson(createDockerConfig(homeDir),"credsStore-does-not-exist",singletonMap("registry1", "credHelper1-does-not-exist"));
                expectedException.expect(MojoExecutionException.class);
                expectedException.expectCause(Matchers.allOf(
                        instanceOf(IOException.class),
                        hasProperty("message",startsWith("Failed to start 'docker-credential-credsStore-does-not-exist get'"))
                ));
                factory.createAuthConfig(isPush,false,null,settings,"roland",null);
            }
        });
    }

    @Test
    public void testDockerLoginDefaultToCredentialsStore() throws MojoExecutionException, IOException {
        executeWithTempHomeDir(new HomeDirExecutor() {
            @Override
            public void exec(File homeDir) throws IOException, MojoExecutionException {
                writeDockerConfigJson(createDockerConfig(homeDir),"credsStore-does-not-exist",singletonMap("registry1", "credHelper1-does-not-exist"));
                expectedException.expect(MojoExecutionException.class);
                expectedException.expectCause(Matchers.allOf(
                        instanceOf(IOException.class),
                        hasProperty("message",startsWith("Failed to start 'docker-credential-credsStore-does-not-exist get'"))
                ));
                factory.createAuthConfig(isPush,false,null,settings,"roland","registry2");
            }
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
            throws IOException, MojoExecutionException {
        writeDockerConfigJson(createDockerConfig(homeDir), "roland", "secret", "roland@jolokia.org", configRegistry);
        AuthConfig config = factory.createAuthConfig(isPush, false, null, settings, "roland", lookupRegistry);
        verifyAuthConfig(config,"roland","secret","roland@jolokia.org");
    }

    private File createDockerConfig(File homeDir)
            throws IOException {
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
        executeWithTempHomeDir(new HomeDirExecutor() {
            @Override
            public void exec(File homeDir) throws IOException, MojoExecutionException {
                createOpenShiftConfig(homeDir,"openshift_simple_config.yaml");
                Map<String,String> authConfigMap = new HashMap<>();
                authConfigMap.put("useOpenShiftAuth","true");
                AuthConfig config = factory.createAuthConfig(isPush, false, authConfigMap, settings, "roland", null);
                verifyAuthConfig(config,"admin","token123",null);
            }
        });
    }

    @Test
    public void testOpenShiftConfigFromSystemProps() throws Exception {
        try {
            System.setProperty("docker.useOpenShiftAuth","true");
            executeWithTempHomeDir(new HomeDirExecutor() {
                @Override
                public void exec(File homeDir) throws IOException, MojoExecutionException {
                    createOpenShiftConfig(homeDir, "openshift_simple_config.yaml");
                    AuthConfig config = factory.createAuthConfig(isPush, false, null, settings, "roland", null);
                    verifyAuthConfig(config, "admin", "token123", null);
                }
            });
        } finally {
            System.getProperties().remove("docker.useOpenShiftAuth");
        }
    }

    @Test
    public void testOpenShiftConfigFromSystemPropsNegative() throws Exception {
        try {
            System.setProperty("docker.useOpenShiftAuth","false");
            executeWithTempHomeDir(new HomeDirExecutor() {
                @Override
                public void exec(File homeDir) throws IOException, MojoExecutionException {
                    createOpenShiftConfig(homeDir, "openshift_simple_config.yaml");
                    Map<String,String> authConfigMap = new HashMap<>();
                    authConfigMap.put("useOpenShiftAuth","true");
                    AuthConfig config = factory.createAuthConfig(isPush, false, authConfigMap, settings, "roland", null);
                    assertNull(config);
                }
            });
        } finally {
            System.getProperties().remove("docker.useOpenShiftAuth");
        }
    }

    @Test
    public void testOpenShiftConfigNotLoggedIn() throws Exception {
        executeWithTempHomeDir(new HomeDirExecutor() {
            @Override
            public void exec(File homeDir) throws IOException, MojoExecutionException {
                createOpenShiftConfig(homeDir,"openshift_nologin_config.yaml");
                Map<String,String> authConfigMap = new HashMap<>();
                authConfigMap.put("useOpenShiftAuth","true");
                expectedException.expect(MojoExecutionException.class);
                expectedException.expectMessage(containsString("~/.kube/config"));
                factory.createAuthConfig(isPush,false,authConfigMap,settings,"roland",null);
            }
        });

    }

    private void createOpenShiftConfig(File homeDir,String testConfig) throws IOException {
        File kubeDir = new File(homeDir,".kube");
        kubeDir.mkdirs();
        File config = new File(kubeDir,"config");
        IOUtil.copy(getClass().getResourceAsStream(testConfig),new FileWriter(config));
    }

    @Test
    public void testSystemPropertyNoPassword() throws Exception {
        expectedException.expect(MojoExecutionException.class);
        expectedException.expectMessage("No docker.password provided for username secret");
        checkException("docker.username");
    }

    private void checkException(String key) throws MojoExecutionException {
        System.setProperty(key, "secret");
        try {
            factory.createAuthConfig(isPush, false, null, settings, null, null);
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    public void testFromPluginConfiguration() throws MojoExecutionException {
        Map pluginConfig = new HashMap();
        pluginConfig.put("username", "roland");
        pluginConfig.put("password", "secret");
        pluginConfig.put("email", "roland@jolokia.org");

        AuthConfig config = factory.createAuthConfig(isPush, false, pluginConfig, settings, null, null);
        verifyAuthConfig(config, "roland", "secret", "roland@jolokia.org");
    }

    @Test
    public void testFromPluginConfigurationPull() throws MojoExecutionException {
        Map pullConfig = new HashMap();
        pullConfig.put("username", "roland");
        pullConfig.put("password", "secret");
        pullConfig.put("email", "roland@jolokia.org");
        Map pluginConfig = new HashMap();
        pluginConfig.put("pull",pullConfig);
        AuthConfig config = factory.createAuthConfig(false, false, pluginConfig, settings, null, null);
        verifyAuthConfig(config, "roland", "secret", "roland@jolokia.org");
    }


    @Test
    public void testFromPluginConfigurationFailed() throws MojoExecutionException {
        Map pluginConfig = new HashMap();
        pluginConfig.put("username","admin");
        expectedException.expect(MojoExecutionException.class);
        expectedException.expectMessage("No 'password' given while using <authConfig> in configuration for mode DEFAULT");
        factory.createAuthConfig(isPush,false,pluginConfig,settings,null,null);
    }

    @Test
    public void testFromSettingsSimple() throws MojoExecutionException {
        setupServers();
        AuthConfig config = factory.createAuthConfig(isPush, false, null, settings, "roland", "test.org");
        assertNotNull(config);
        verifyAuthConfig(config, "roland", "secret", "roland@jolokia.org");
    }

    @Test
    public void testFromSettingsDefault() throws MojoExecutionException {
        setupServers();
        AuthConfig config = factory.createAuthConfig(isPush, false, null, settings, "fabric8io", "test.org");
        assertNotNull(config);
        verifyAuthConfig(config, "fabric8io", "secret2", "fabric8io@redhat.com");
    }

    @Test
    public void testFromSettingsDefault2() throws MojoExecutionException {
        setupServers();
        AuthConfig config = factory.createAuthConfig(isPush, false, null, settings, "tanja", null);
        assertNotNull(config);
        verifyAuthConfig(config,"tanja","doublesecret","tanja@jolokia.org");
    }

    @Test
    public void testWrongUserName() throws IOException, MojoExecutionException {
        executeWithTempHomeDir(new HomeDirExecutor() {
            @Override
            public void exec(File homeDir) throws IOException, MojoExecutionException {
                setupServers();
                assertNull(factory.createAuthConfig(isPush,false,null,settings,"roland","another.repo.org"));
            }
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
