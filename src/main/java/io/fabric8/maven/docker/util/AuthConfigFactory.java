package io.fabric8.maven.docker.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.ecr.EcrExtendedAuth;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
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

    // Properties for specifying username, password (can be encrypted), email and authtoken (not used yet)
    // + whether to check for OpenShift authentication
    public static final String AUTH_USERNAME = "username";
    public static final String AUTH_PASSWORD = "password";
    public static final String AUTH_EMAIL = "email";
    public static final String AUTH_AUTHTOKEN = "authToken";
    private static final String AUTH_USE_OPENSHIFT_AUTH = "useOpenShiftAuth";

    static final String DOCKER_LOGIN_DEFAULT_REGISTRY = "https://index.docker.io/v1/";

    private final PlexusContainer container;
    private Logger log;
    private static final String[] DEFAULT_REGISTRIES = new String[]{
            "docker.io", "index.docker.io", "registry.hub.docker.com"
    };

    /**
     * Constructor which should be used during startup phase of a plugin
     *
     * @param container the container used for do decryption of passwords
     */
    public AuthConfigFactory(PlexusContainer container) {
        this.container = container;
    }

    public void setLog(Logger log) {
        this.log = log;
    }

    /**
     * Create an authentication config object which can be used for communication with a Docker registry
     *
     * The authentication information is looked up at various places (in this order):
     *
     * <ul>
     *    <li>From system properties</li>
     *    <li>From the provided map which can contain key-value pairs</li>
     *    <li>From the openshift settings in ~/.config/kube</li>
     *    <li>From the Maven settings stored typically in ~/.m2/settings.xml</li>
     *    <li>From the Docker settings stored in ~/.docker/config.json</li>
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
     *  If the repository is in an aws ecr registry and skipExtendedAuth is not true, if found
     *  credentials are not from docker settings, they will be interpreted as iam credentials
     *  and exchanged for ecr credentials.
     *
     * @param isPush if true this AuthConfig is created for a push, if false it's for a pull
     * @param skipExtendedAuth if false, do not execute extended authentication methods
     * @param authConfig String-String Map holding configuration info from the plugin's configuration. Can be <code>null</code> in
     *                   which case the settings are consulted.
     * @param settings the global Maven settings object
     * @param user user to check for
     * @param registry registry to use, might be null in which case a default registry is checked,
     * @return the authentication configuration or <code>null</code> if none could be found
     *
     * @throws MojoFailureException
     */
    public AuthConfig createAuthConfig(boolean isPush, boolean skipExtendedAuth, Map authConfig, Settings settings, String user, String registry)
            throws MojoExecutionException {

        AuthConfig ret = createStandardAuthConfig(isPush, authConfig, settings, user, registry);
        if (ret != null) {
            if (registry == null || skipExtendedAuth) {
                return ret;
            }
            try {
                return extendedAuthentication(ret, registry);
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }

        // Finally check ~/.docker/config.json
        ret = getAuthConfigFromDockerConfig(registry);
        if (ret != null) {
            log.debug("AuthConfig: credentials from ~.docker/config.json");
            return ret;
        }

        log.debug("AuthConfig: no credentials found");
        return null;
    }


    /**
     * Try various extended authentication method.  Currently only supports amazon ECR
     *
     * @param standardAuthConfig The locally stored credentials.
     * @param registry The registry to authenticated against.
     * @return The given credentials, if registry does not need extended authentication;
     * else, the credentials after authentication.
     * @throws IOException
     * @throws MojoExecutionException
     */
    private AuthConfig extendedAuthentication(AuthConfig standardAuthConfig, String registry) throws IOException, MojoExecutionException {
        EcrExtendedAuth ecr = new EcrExtendedAuth(log, registry);
        if (ecr.isAwsRegistry()) {
            return ecr.extendedAuth(standardAuthConfig);
        }
        return standardAuthConfig;
    }

    /**
     * Create an authentication config object which can be used for communication with a Docker registry
     *
     * The authentication information is looked up at various places (in this order):
     *
     * <ul>
     *    <li>From system properties</li>
     *    <li>From the provided map which can contain key-value pairs</li>
     *    <li>From the openshift settings in ~/.config/kube</li>
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
     *
     * @param isPush if true this AuthConfig is created for a push, if false it's for a pull
     * @param authConfigMap String-String Map holding configuration info from the plugin's configuration. Can be <code>null</code> in
     *                   which case the settings are consulted.
     * @param settings the global Maven settings object
     * @param user user to check for
     * @param registry registry to use, might be null in which case a default registry is checked,
     * @return the authentication configuration or <code>null</code> if none could be found
     *
     * @throws MojoFailureException
     */
    private AuthConfig createStandardAuthConfig(boolean isPush, Map authConfigMap, Settings settings, String user, String registry)
            throws MojoExecutionException {
        AuthConfig ret;

        // Check first for specific configuration based on direction (pull or push), then for a default value
        for (LookupMode lookupMode : new LookupMode[] { getLookupMode(isPush), LookupMode.DEFAULT }) {
            // System properties docker.username and docker.password always take precedence
            ret = getAuthConfigFromSystemProperties(lookupMode);
            if (ret != null) {
                log.debug("AuthConfig: credentials from system properties");
                return ret;
            }

            // Check for openshift authentication either from the plugin config or from system props
            ret = getAuthConfigFromOpenShiftConfig(lookupMode, authConfigMap);
            if (ret != null) {
                log.debug("AuthConfig: OpenShift credentials");
                return ret;
            }

            // Get configuration from global plugin config
            ret = getAuthConfigFromPluginConfiguration(lookupMode, authConfigMap);
            if (ret != null) {
                log.debug("AuthConfig: credentials from plugin config");
                return ret;
            }
        }

        // ===================================================================
        // These are lookups based on registry only, so the direction (push or pull) doesn't matter:

        // Now lets lookup the registry & user from ~/.m2/setting.xml
        ret = getAuthConfigFromSettings(settings, user, registry);
        if (ret != null) {
            log.debug("AuthConfig: credentials from ~/.m2/setting.xml");
            return ret;
        }

        // No authentication found
        return null;
    }

    // ===================================================================================================

    private AuthConfig getAuthConfigFromSystemProperties(LookupMode lookupMode) throws MojoExecutionException {
        Properties props = System.getProperties();
        String userKey = lookupMode.asSysProperty(AUTH_USERNAME);
        String passwordKey = lookupMode.asSysProperty(AUTH_PASSWORD);
        if (props.containsKey(userKey)) {
            if (!props.containsKey(passwordKey)) {
                throw new MojoExecutionException("No " + passwordKey + " provided for username " + props.getProperty(userKey));
            }
            return new AuthConfig(props.getProperty(userKey),
                                  decrypt(props.getProperty(passwordKey)),
                                  props.getProperty(lookupMode.asSysProperty(AUTH_EMAIL)),
                                  props.getProperty(lookupMode.asSysProperty(AUTH_AUTHTOKEN)));
        } else {
            return null;
        }
    }

    private AuthConfig getAuthConfigFromOpenShiftConfig(LookupMode lookupMode, Map authConfigMap) throws MojoExecutionException {
        Properties props = System.getProperties();
        String useOpenAuthModeProp = lookupMode.asSysProperty(AUTH_USE_OPENSHIFT_AUTH);
        // Check for system property
        if (props.containsKey(useOpenAuthModeProp)) {
            boolean useOpenShift = Boolean.valueOf(props.getProperty(useOpenAuthModeProp));
            if (useOpenShift) {
                AuthConfig ret = parseOpenShiftConfig();
                if (ret == null) {
                    throw new MojoExecutionException("System property " + useOpenAuthModeProp + " " +
                                                     "set, but not active user and/or token found in ~/.config/kube. " +
                                                     "Please use 'oc login' for connecting to OpenShift.");
                }
                return ret;
            } else {
                return null;
            }
        }

        // Check plugin config
        Map mapToCheck = getAuthConfigMapToCheck(lookupMode,authConfigMap);
        if (mapToCheck != null && mapToCheck.containsKey(AUTH_USE_OPENSHIFT_AUTH) &&
            Boolean.valueOf((String) mapToCheck.get(AUTH_USE_OPENSHIFT_AUTH))) {
            AuthConfig ret = parseOpenShiftConfig();
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

    private AuthConfig getAuthConfigFromPluginConfiguration(LookupMode lookupMode, Map authConfig) throws MojoExecutionException {
        Map mapToCheck = getAuthConfigMapToCheck(lookupMode,authConfig);

        if (mapToCheck != null && mapToCheck.containsKey(AUTH_USERNAME)) {
            if (!mapToCheck.containsKey(AUTH_PASSWORD)) {
                throw new MojoExecutionException("No 'password' given while using <authConfig> in configuration for mode " + lookupMode);
            }
            Map<String, String> cloneConfig = new HashMap<>(mapToCheck);
            cloneConfig.put(AUTH_PASSWORD, decrypt(cloneConfig.get(AUTH_PASSWORD)));
            return new AuthConfig(cloneConfig);
        } else {
            return null;
        }
    }

    private AuthConfig getAuthConfigFromSettings(Settings settings, String user, String registry) throws MojoExecutionException {
        Server defaultServer = null;
        Server found;
        for (Server server : settings.getServers()) {
            String id = server.getId();

            // Remember a default server without user as fallback for later
            if (defaultServer == null) {
                defaultServer = checkForServer(server, id, registry, null);
            }
            // Check for specific server with user part
            found = checkForServer(server, id, registry, user);
            if (found != null) {
                return createAuthConfigFromServer(found);
            }
        }
        return defaultServer != null ? createAuthConfigFromServer(defaultServer) : null;
    }

    private AuthConfig getAuthConfigFromDockerConfig(String registry) throws MojoExecutionException {
        JSONObject dockerConfig = readDockerConfig();
        if (dockerConfig == null) {
            return null;
        }
        String registryToLookup = registry != null ? registry : DOCKER_LOGIN_DEFAULT_REGISTRY;

        if (dockerConfig.has("credHelpers") || dockerConfig.has("credsStore")) {
            if (dockerConfig.has("credHelpers")) {
                final JSONObject credHelpers = dockerConfig.getJSONObject("credHelpers");
                if (credHelpers.has(registryToLookup)) {
                    return extractAuthConfigFromCredentialsHelper(registryToLookup, credHelpers.getString(registryToLookup));
                }
            }
            if (dockerConfig.has("credsStore")) {
                return extractAuthConfigFromCredentialsHelper(registryToLookup, dockerConfig.getString("credsStore"));
            }
            return null;
        }

        if (dockerConfig.has("auths")) {
            return extractAuthConfigFromAuths(registryToLookup, dockerConfig.getJSONObject("auths"));
        }

        return null;
    }

    private AuthConfig extractAuthConfigFromAuths(String registryToLookup, JSONObject auths) {
        JSONObject credentials = getCredentialsNode(auths,registryToLookup);
        if (credentials == null || !credentials.has("auth")) {
            return null;
        }
        String auth = credentials.getString("auth");
        String email = credentials.has("email") ? credentials.getString("email") : null;
        return new AuthConfig(auth,email);
    }

    private AuthConfig extractAuthConfigFromCredentialsHelper(String registryToLookup, String credConfig) throws MojoExecutionException {
        CredentialHelperClient credentialHelper = new CredentialHelperClient(log, credConfig);
        log.debug("AuthConfig: credentials from credential helper/store %s version %s",
                  credentialHelper.getName(),
                  credentialHelper.getVersion());
        return credentialHelper.getAuthConfig(registryToLookup);
    }

    private JSONObject getCredentialsNode(JSONObject auths,String registryToLookup) {
        if (auths.has(registryToLookup)) {
            return auths.getJSONObject(registryToLookup);
        }
        String registryWithScheme = EnvUtil.ensureRegistryHttpUrl(registryToLookup);
        if (auths.has(registryWithScheme)) {
            return auths.getJSONObject(registryWithScheme);
        }
        return null;
    }

    // =======================================================================================================

    private Map getAuthConfigMapToCheck(LookupMode lookupMode, Map authConfigMap) {
        String configMapKey = lookupMode.getConfigMapKey();
        if (configMapKey == null) {
            return authConfigMap;
        }
        if (authConfigMap != null) {
            return (Map) authConfigMap.get(configMapKey);
        }
        return null;
    }

    // Parse OpenShift config to get credentials, but return null if not found
    private AuthConfig parseOpenShiftConfig() {
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
        if (file.exists() && file.length() != 0) {
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

    // ========================================================================================
    // Mode which direction to lookup (pull, push or default value for both, pull and push)

    private LookupMode getLookupMode(boolean isPush) {
        return isPush ? LookupMode.PUSH : LookupMode.PULL;
    }

    private enum LookupMode {
        PUSH("docker.push.","push"),
        PULL("docker.pull.","pull"),
        DEFAULT("docker.",null);

        private final String sysPropPrefix;
        private String configMapKey;

        LookupMode(String sysPropPrefix,String configMapKey) {
            this.sysPropPrefix = sysPropPrefix;
            this.configMapKey = configMapKey;
        }

        public String asSysProperty(String prop) {
            return sysPropPrefix + prop;
        }

        public String getConfigMapKey() {
            return configMapKey;
        }
    }

}
