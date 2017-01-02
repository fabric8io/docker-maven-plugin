package io.fabric8.maven.docker.util;

import mockit.*;
import mockit.integration.junit4.JMockit;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.Base64;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import io.fabric8.maven.docker.access.AuthConfig;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 29.07.14
 */
@RunWith(JMockit.class)
public class AuthConfigFactoryTest {

    public static final String ECR_NAME = "123456789012.dkr.ecr.bla.amazonaws.com";

    @Mocked
    Settings settings;

    @Mocked
    private Logger log;

    private AuthConfigFactory factory;

    private boolean isPush = true;

    public static final class MockSecDispatcher extends MockUp<SecDispatcher> {
        @Mock
        public String decrypt(String password) {
            return password;
        }
    }

    @Mocked
    PlexusContainer container;

    @Before
    public void containerSetup() throws ComponentLookupException {
        final SecDispatcher secDispatcher = new MockSecDispatcher().getMockInstance();
        new Expectations() {{
            container.lookup(SecDispatcher.ROLE, "maven"); minTimes = 0; result = secDispatcher;

        }};
        factory = new AuthConfigFactory(container);
        factory.setLog(log);
    }

    @Test
    public void testEmpty() throws Exception {
        assertNull(factory.createAuthConfig(isPush, false, null, settings, null, "blubberbla:1611"));
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


    @Test
    public void testDockerLogin() throws Exception {
        executeWithTempHomeDir(new HomeDirExecutor() {
            @Override
            public void exec(File homeDir) throws IOException, MojoExecutionException {
                checkDockerLogin(homeDir,AuthConfigFactory.DOCKER_LOGIN_DEFAULT_REGISTRY,null);
                checkDockerLogin(homeDir,"localhost:5000","localhost:5000");
                checkDockerLogin(homeDir,"https://localhost:5000","localhost:5000");
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

    private void checkDockerLogin(File homeDir,String configRegistry, String lookupRegistry)
            throws IOException, MojoExecutionException {
        createDockerConfig(homeDir, "roland", "secret", "roland@jolokia.org", configRegistry);
        AuthConfig config = factory.createAuthConfig(isPush, false, null, settings, "roland", lookupRegistry);
        verifyAuthConfig(config,"roland","secret","roland@jolokia.org");
    }

    private void createDockerConfig(File homeDir, String user, String password, String email, String registry)
            throws IOException {
        File dockerDir = new File(homeDir,".docker");
        dockerDir.mkdirs();
        writeDockerConfigJson(dockerDir, user, password, email, registry);
    }

    private void writeDockerConfigJson(File dockerDir, String user, String password,
                                       String email, String registry) throws IOException {
        File configFile = new File(dockerDir,"config.json");
        JSONObject config = new JSONObject();
        JSONObject auths = new JSONObject();
        JSONObject value = new JSONObject();
        value.put("auth", new String(Base64.encodeBase64(new String(user + ":" + password).getBytes())));
        value.put("email",email);
        auths.put(registry,value);
        config.put("auths",auths);
        Writer writer = new FileWriter(configFile);
        config.write(writer);
        writer.close();
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

    @Test(expected = MojoExecutionException.class)
    public void testOpenShiftConfigNotLoggedIn() throws Exception {
        executeWithTempHomeDir(new HomeDirExecutor() {
            @Override
            public void exec(File homeDir) throws IOException, MojoExecutionException {
                createOpenShiftConfig(homeDir,"openshift_nologin_config.yaml");
                Map<String,String> authConfigMap = new HashMap<>();
                authConfigMap.put("useOpenShiftAuth","true");
                AuthConfig config = factory.createAuthConfig(isPush, false, authConfigMap, settings, "roland", null);
            }
        });

    }

    private void createOpenShiftConfig(File homeDir,String testConfig) throws IOException {
        File kubeDir = new File(homeDir,".kube");
        kubeDir.mkdirs();
        File config = new File(kubeDir,"config");
        IOUtil.copy(getClass().getResourceAsStream(testConfig),new FileWriter(config));
    }

    @Test(expected = MojoExecutionException.class)
    public void testSystemPropertyNoPassword() throws Exception {
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


    @Test(expected = MojoExecutionException.class)
    public void testFromPluginConfigurationFailed() throws MojoExecutionException {
        Map pluginConfig = new HashMap();
        pluginConfig.put("username", "admin");
        factory.createAuthConfig(isPush, false, pluginConfig, settings, null, null);
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
    public void testWrongUserName() throws MojoExecutionException {
        setupServers();
        assertNull(factory.createAuthConfig(isPush, false, null, settings, "roland", "another.repo.org"));
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
        JSONObject params = new JSONObject(new String(Base64.decodeBase64(config.toHeaderValue().getBytes())));
        assertEquals(username,params.get("username"));
        assertEquals(password,params.get("password"));
        if (email != null) {
            assertEquals(email, params.get("email"));
        }
    }

}
