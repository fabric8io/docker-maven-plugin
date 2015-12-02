package org.jolokia.docker.maven.util;

import java.lang.reflect.Method;
import java.util.*;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jolokia.docker.maven.access.AuthConfig;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

/**
 * Factory for creating docker specific authentication configuration
 *
 * @author roland
 * @since 29.07.14
 */
public class AuthConfigFactory {

    // System properties for specifying username, password (can be encrypted), email and authtoken (not used yet)
    private static final String DOCKER_USERNAME = "docker.username";
    private static final String DOCKER_PASSWORD = "docker.password";
    private static final String DOCKER_EMAIL = "docker.email";
    private static final String DOCKER_AUTH = "docker.authToken";

    private final PlexusContainer container;
    public static final String[] DEFAULT_REGISTRIES = new String[]{
            "docker.io", "index.docker.io", "registry.hub.docker.com"
    };

    /**
     * Constructor which should be used during startup phase of a plugin
     *
     * @param container the container used for do decrytion of passwords
     */
    public AuthConfigFactory(PlexusContainer container) {
        this.container = container;
    }

    /**
     * Create an authentication config object which can be used for communication with a Docker registry
     *
     * The authentication information is looked up at various places (in this order):
     *
     * <ul>
     *    <li>From system properties</li>
     *    <li>From the provided map which can contain key-value pairs</li>
     *    <li>From the Maven settings stored typically in ~/.m2/settings.xml</li>
     * </ul>
     *
     * The following properties (prefix with 'docker.') and config key are evaluated:
     *
     * <ul>
     *     <li>username: User to authenticate</li>
     *     <li>password: Password to authenticate. Can be encrypted</li>
     *     <li>email: Optional EMail address which is send to the registry, too</li>
     * </ul>
     *
     * @param authConfig String-String Map holding configuration info from the plugin's configuration. Can be <code>null</code> in
     *                   which case the settings are consulted.
     * @param settings the global Maven settings object
     * @param user user to check for
     * @param registry registry to use, might be null in which case a default registry is checked,
     * @return the authentication configuration or <code>null</code> if none could be found
     *
     * @throws MojoFailureException
     */
    public AuthConfig createAuthConfig(Map authConfig, Settings settings, String user, String registry) throws MojoExecutionException {
        Properties props = System.getProperties();
        if (props.containsKey(DOCKER_USERNAME) || props.containsKey(DOCKER_PASSWORD)) {
            return getAuthConfigFromProperties(props, registry);
        }
        if (authConfig != null) {
            return getAuthConfigFromPluginConfiguration(authConfig,registry);
        }
        return getAuthConfigFromSettings(settings,user,registry);
    }

    // ===================================================================================================

    private AuthConfig getAuthConfigFromProperties(Properties props, String registry) throws
                                                                      MojoExecutionException {
        if (!props.containsKey(DOCKER_USERNAME)) {
            throw new MojoExecutionException("No " + DOCKER_USERNAME + " given when using authentication");
        }
        if (!props.containsKey(DOCKER_PASSWORD)) {
            throw new MojoExecutionException("No " + DOCKER_PASSWORD + " provided for username " + props.getProperty(DOCKER_USERNAME));
        }
        return new AuthConfig(props.getProperty(DOCKER_USERNAME),
                              decrypt(props.getProperty(DOCKER_PASSWORD)),
                              props.getProperty(DOCKER_EMAIL),
                              props.getProperty(DOCKER_AUTH), registry);
    }

    private AuthConfig getAuthConfigFromPluginConfiguration(Map authConfig, String registry) throws MojoExecutionException {
        for (String key : new String[] { "username", "password"}) {
            if (!authConfig.containsKey(key)) {
                throw new MojoExecutionException("No '" + key + "' given while using <authConfig> in configuration");
            }
        }
        Map<String,String> cloneConfig = new HashMap<String,String>(authConfig);
        cloneConfig.put("password",decrypt(cloneConfig.get("password")));
        cloneConfig.put("serveraddress",registry);
        return new AuthConfig(cloneConfig);
    }

    private AuthConfig getAuthConfigFromSettings(Settings settings, String user, String registry) throws MojoExecutionException {
        Server defaultServer = null;
        Server found = null;
        for (Server server : settings.getServers()) {
            String id = server.getId();

            if (defaultServer == null) {
                defaultServer = checkForServer(server, id, registry, null);
            }
            found = checkForServer(server, id, registry, user);
            if (found != null) {
                return createAuthConfigFromServer(found, registry);
            }
        }
        return defaultServer != null ? createAuthConfigFromServer(defaultServer, registry) : null;
    }

    private Server checkForServer(Server server, String id, String registry, String user) {

        String[] registries = registry != null ? new String[] { registry } : DEFAULT_REGISTRIES;
        for (String reg : registries) {
            if (id.equals(user == null ? reg : reg + "/" + user)) {
                return server;
            }
        }
        return null;
    }

    private String decrypt(String password) throws MojoExecutionException {
        try {
            // Done by reflection since I have classloader issues otherwise
            Object secDispatcher = container.lookup(SecDispatcher.ROLE, "maven");
            Method method = secDispatcher.getClass().getMethod("decrypt",String.class);
            return (String) method.invoke(secDispatcher,password);
        } catch (ComponentLookupException e) {
            throw new MojoExecutionException("Error looking security dispatcher",e);
        } catch (ReflectiveOperationException e) {
            throw new MojoExecutionException("Cannot decrypt password: " + e.getCause(),e);
        }
    }

    private AuthConfig createAuthConfigFromServer(Server server, String registry) throws MojoExecutionException {
        return new AuthConfig(
                server.getUsername(),
                decrypt(server.getPassword()),
                extractFromServerConfiguration(server.getConfiguration(), "email"),
                extractFromServerConfiguration(server.getConfiguration(), "auth"),
                registry
        );
    }

    private String extractFromServerConfiguration(Object configuration, String prop) {
        if (configuration != null) {
            Xpp3Dom dom = (Xpp3Dom) configuration;
            Xpp3Dom element = dom.getChild(prop);
            if (element != null) {
                return element.getValue();
            }
        }
        return null;
    }

}
