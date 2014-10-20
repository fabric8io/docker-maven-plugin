package org.jolokia.docker.maven.util;

import java.util.HashMap;
import java.util.Map;

import mockit.*;
import mockit.integration.junit4.JMockit;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jolokia.docker.maven.access.AuthConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import static mockit.Deencapsulation.getField;
import static org.junit.Assert.*;

/**
 * @author roland
 * @since 29.07.14
 */
@RunWith(JMockit.class)
public class AuthConfigHandlerTest {

    @Mocked
    Settings settings;

    private AuthConfigFactory factory;

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
        new NonStrictExpectations() {{
            container.lookup(SecDispatcher.ROLE, "maven"); result = secDispatcher;

        }};
        factory = new AuthConfigFactory(container);
    }

    @Test
    public void testEmpty() throws Exception {
        assertNull(factory.createAuthConfig(null, "test/test", settings));
    }

    @Test
    public void testSystemProperty() throws Exception {
        System.setProperty("docker.username","roland");
        System.setProperty("docker.password", "secret");
        System.setProperty("docker.email", "roland@jolokia.org");
        try {
            AuthConfig config = factory.createAuthConfig(null,"test/test",settings);
            verifyAuthConfig(config,"roland","secret","roland@jolokia.org");
        } finally {
            System.clearProperty("docker.username");
            System.clearProperty("docker.password");
            System.clearProperty("docker.email");
        }
    }

    @Test(expected = MojoExecutionException.class)
    public void testSystemPropertyNoUser() throws Exception {
        checkException("docker.password");
    }

    @Test(expected = MojoExecutionException.class)
    public void testSystemPropertyNoPassword() throws Exception {
        checkException("docker.username");
    }

    private void checkException(String key) throws MojoExecutionException {
        System.setProperty(key, "secret");
        try {
            factory.createAuthConfig(null, "test/test", settings);
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

        AuthConfig config = factory.createAuthConfig(pluginConfig,"test/test",settings);
        verifyAuthConfig(config,"roland","secret","roland@jolokia.org");
    }


    @Test(expected = MojoExecutionException.class)
    public void testFromPluginConfigurationFailed() throws MojoExecutionException {
        Map pluginConfig = new HashMap();
        pluginConfig.put("password", "secret");
        factory.createAuthConfig(pluginConfig, "test/test", settings);
    }

    @Test
    public void testFromSettings() throws MojoExecutionException {
        new NonStrictExpectations() {{
            settings.getServer("test.org");
            Server server = new Server();
            server.setPassword("secret");
            server.setUsername("roland");
            Xpp3Dom dom = new Xpp3Dom("configuration");
            Xpp3Dom email = new Xpp3Dom("email");
            email.setValue("roland@jolokia.org");
            dom.addChild(email);
            server.setConfiguration(dom);
            result = server;
        }};

        AuthConfig config = factory.createAuthConfig(null,"test.org/test",settings);
        verifyAuthConfig(config,"roland","secret","roland@jolokia.org");
    }

    private void verifyAuthConfig(AuthConfig config, String username, String password, String email) {
        Map params = getField(config,"params");
        assertEquals(username,params.get("username"));
        assertEquals(password,params.get("password"));
        assertEquals(email, params.get("email"));
    }

}
