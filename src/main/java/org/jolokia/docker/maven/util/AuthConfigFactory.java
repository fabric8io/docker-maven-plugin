package org.jolokia.docker.maven.util;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jolokia.docker.maven.access.AuthConfig;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.yaml.snakeyaml.Yaml;

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
    private static final String DOCKER_USE_OPENSHIFT_AUTH = "docker.useOpenShiftAuth";

    static final String DOCKER_LOGIN_DEFAULT_REGISTRY = "https://index.docker.io/v1/";

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
     * @param authConfigMap String-String Map holding configuration info from the plugin's configuration. Can be <code>null</code> in
     *                   which case the settings are consulted.
     * @param settings the global Maven settings object
     * @param user user to check for
     * @param registry registry to use, might be null in which case a default registry is checked,
     * @return the authentication configuration or <code>null</code> if none could be found
     *
     * @throws MojoFailureException
     */
    public AuthConfig createAuthConfig(Map authConfigMap, Settings settings, String user, String registry) throws
                                                                                                         MojoExecutionException {
        AuthConfig ret;

        // System properties docker.username and docker.password always take precedence
        ret = getAuthConfigFromSystemProperties();
        if (ret != null) {
            return ret;
        }

        // Check for openshift authentication either from the plugin config or from system props
        ret = getAuthConfigFromOpenShiftConfig(authConfigMap);
        if (ret != null) {
            return ret;
        }

        // Get configuration from global plugin config
        ret = getAuthConfigFromPluginConfiguration(authConfigMap);
        if (ret != null) {
            return ret;
        }

        // Now lets lookup the registry & user from ~/.m2/setting.xml
        ret = getAuthConfigFromSettings(settings, user, registry);
        if (ret != null) {
            return ret;
        }

        // Finally check ~/.docker/config.json
        ret = getAuthConfigFromDockerConfig(registry);
        if (ret != null) {
            return ret;
        }

        // No authentication found
        return null;
    }

    // ===================================================================================================

    private AuthConfig getAuthConfigFromOpenShiftConfig(Map authConfigMap) throws MojoExecutionException {
        Properties props = System.getProperties();

        // Check for system property
        if (props.containsKey(DOCKER_USE_OPENSHIFT_AUTH)) {
            boolean useOpenShift = Boolean.valueOf(props.getProperty(DOCKER_USE_OPENSHIFT_AUTH));
            if (useOpenShift) {
                AuthConfig ret = getAuthFromOpenShiftConfig();
                if (ret == null) {
                    throw new MojoExecutionException("System property " + DOCKER_USE_OPENSHIFT_AUTH + " " +
                                                     "set, but not active user and/or token found in ~/.config/kube. " +
                                                     "Please use 'oc login' for connecting to OpenShift.");
                }
                return ret;
            } else {
                return null;
            }
        }

        // Check plugin config
        if (authConfigMap != null && authConfigMap.containsKey("useOpenShiftAuth") &&
            Boolean.valueOf((String) authConfigMap.get("useOpenShiftAuth"))) {
            AuthConfig ret = getAuthFromOpenShiftConfig();
            if (ret == null) {
                throw new MojoExecutionException("Authentication configured for OpenShift, but no active user and/or " +
                                                 "token found in ~/.config/kube. Please use 'oc login' for " +
                                                 "connecting to OpenShift.");
            }
            return ret;
        } else {
            return null;
        }
    }

    private AuthConfig getAuthConfigFromSystemProperties() throws MojoExecutionException {
        Properties props = System.getProperties();
        if (props.containsKey(DOCKER_USERNAME) || props.containsKey(DOCKER_PASSWORD)) {
            if (!props.containsKey(DOCKER_USERNAME)) {
                throw new MojoExecutionException("No " + DOCKER_USERNAME + " given when using authentication");
            }
            if (!props.containsKey(DOCKER_PASSWORD)) {
                throw new MojoExecutionException("No " + DOCKER_PASSWORD + " provided for username " + props.getProperty(DOCKER_USERNAME));
            }
            return new AuthConfig(props.getProperty(DOCKER_USERNAME),
                                  decrypt(props.getProperty(DOCKER_PASSWORD)),
                                  props.getProperty(DOCKER_EMAIL),
                                  props.getProperty(DOCKER_AUTH));
        } else {
            return null;
        }
    }

    private AuthConfig getAuthConfigFromPluginConfiguration(Map authConfig) throws MojoExecutionException {
        if (authConfig != null && authConfig.containsKey("username")) {
            if (!authConfig.containsKey("password")) {
                throw new MojoExecutionException("No 'password' given while using <authConfig> in configuration");
            }
            Map<String, String> cloneConfig = new HashMap<String, String>(authConfig);
            cloneConfig.put("password", decrypt(cloneConfig.get("password")));
            return new AuthConfig(cloneConfig);
        } else {
            return null;
        }
    }

    private AuthConfig getAuthConfigFromDockerConfig(String registry) throws MojoExecutionException {
        JSONObject dockerConfig = readDockerConfig();
        if (dockerConfig != null && dockerConfig.has("auths")) {
            JSONObject auths = dockerConfig.getJSONObject("auths");
            String registryToLookup = registry != null ? registry : DOCKER_LOGIN_DEFAULT_REGISTRY;
            if (auths.has(registryToLookup)) {
                JSONObject entry = auths.getJSONObject(registryToLookup);
                if (entry.has("auth")) {
                    String auth = entry.getString("auth");
                    String email = entry.has("email") ? entry.getString("email") : null;
                    return new AuthConfig(auth,email);
                }
            }
        }
        return null;
    }

    // Parse OpenShift config to get credentials, but return null if not found
    private AuthConfig getAuthFromOpenShiftConfig() {
        Map kubeConfig = readKubeConfig();
        if (kubeConfig != null) {
            String currentContextName = (String) kubeConfig.get("current-context");
            if (currentContextName != null) {
                for (Map contextMap : (List<Map>) kubeConfig.get("contexts")) {
                    if (currentContextName.equals(contextMap.get("name"))) {
                        Map context = (Map) contextMap.get("context");
                        if (context != null) {
                            String userName = (String) context.get("user");
                            if (userName != null) {
                                List<Map> users = (List<Map>) kubeConfig.get("users");
                                if (users != null) {
                                    for (Map userMap : users) {
                                        if (userName.equals(userMap.get("name"))) {
                                            Map user = (Map) userMap.get("user");
                                            if (user != null) {
                                                String token = (String) user.get("token");
                                                if (token != null) {
                                                    // Strip off stuff after username
                                                    Matcher matcher = Pattern.compile("^([^/]+).*$").matcher(userName);
                                                    return new AuthConfig(matcher.matches() ? matcher.group(1) : userName,
                                                                          token, null, null);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // No user found
        return null;
    }

    private JSONObject readDockerConfig() {
        Reader reader = getFileReaderFromHomeDir(".docker/config.json");
        return reader != null ? new JSONObject(new JSONTokener(reader)) : null;
    }

    private Map<String,?> readKubeConfig() {
        Reader reader = getFileReaderFromHomeDir(".kube/config");
        if (reader != null) {
            Yaml ret = new Yaml();
            return (Map<String, ?>) ret.load(reader);
        }
        return null;
    }

    private Reader getFileReaderFromHomeDir(String path) {
        File file = new File(getHomeDir(),path);
        if (file.exists()) {
            try {
                return new FileReader(file);
            } catch (FileNotFoundException e) {
                // Shouldnt happen. Nevertheless ...
                throw new IllegalStateException("Cannot find " + file,e);
            }
        } else {
            return null;
        }
    }

    private File getHomeDir() {
        String homeDir = System.getProperty("user.home");
        if (homeDir == null) {
            homeDir = System.getenv("HOME");
        }
        return new File(homeDir);
    }


    private AuthConfig getAuthConfigFromSettings(Settings settings, String user, String registry) throws MojoExecutionException {
        Server defaultServer = null;
        Server found;
        for (Server server : settings.getServers()) {
            String id = server.getId();

            if (defaultServer == null) {
                defaultServer = checkForServer(server, id, registry, null);
            }
            found = checkForServer(server, id, registry, user);
            if (found != null) {
                return createAuthConfigFromServer(found);
            }
        }
        return defaultServer != null ? createAuthConfigFromServer(defaultServer) : null;
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

    private AuthConfig createAuthConfigFromServer(Server server) throws MojoExecutionException {
        return new AuthConfig(
                server.getUsername(),
                decrypt(server.getPassword()),
                extractFromServerConfiguration(server.getConfiguration(), "email"),
                extractFromServerConfiguration(server.getConfiguration(), "auth")
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
