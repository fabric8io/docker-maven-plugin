package io.fabric8.maven.docker.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import com.google.common.net.UrlEscapers;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.ecr.EcrExtendedAuth;

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
     * @param skipExtendedAuth if true, do not execute extended authentication methods
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
            log.debug("AuthConfig: credentials from ~/.docker/config.json");
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
     * The following properties (prefix with 'docker.' or 'registry.') and config key are evaluated:
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
        for (LookupMode lookupMode : new LookupMode[] { getLookupMode(isPush), LookupMode.DEFAULT, LookupMode.REGISTRY}) {
            // System properties docker.username and docker.password always take precedence
            ret = getAuthConfigFromSystemProperties(lookupMode);
            if (ret != null) {
                log.debug("AuthConfig: credentials from system properties");
                return ret;
            }

            // Check for openshift authentication either from the plugin config or from system props
            if (lookupMode != LookupMode.REGISTRY) {
                ret = getAuthConfigFromOpenShiftConfig(lookupMode, authConfigMap);
                if (ret != null) {
                    log.debug("AuthConfig: OpenShift credentials");
                    return ret;
                }
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

        // check EC2 instance role if registry is ECR
        if (EcrExtendedAuth.isAwsRegistry(registry)) {
            try {
                ret = getAuthConfigFromEC2InstanceRole();
            } catch (ConnectTimeoutException ex) {
                log.debug("Connection timeout while retrieving instance meta-data, likely not an EC2 instance (%s)",
                        ex.getMessage());
            } catch (IOException ex) {
                // don't make that an error since it may fail if not run on an EC2 instance
                log.warn("Error while retrieving EC2 instance credentials: %s", ex.getMessage());
            }
            if (ret != null) {
                log.debug("AuthConfig: credentials from EC2 instance role");
                return ret;
            }

            try {
                ret = getAuthConfigFromECSTaskRole();
            } catch (ConnectTimeoutException ex) {
                log.debug("Connection timeout while retrieving ECS meta-data, likely not an ECS instance (%s)",
                        ex.getMessage());
            } catch (IOException ex) {
                log.warn("Error while retrieving ECS Task role credentials: %s", ex.getMessage());
            }
            if (ret != null) {
                log.debug("AuthConfig: credentials from ECS Task role");
                return ret;
            }
        }

        // No authentication found
        return null;
    }

    // ===================================================================================================


    // if the local credentials don't contain user and password, use EC2 instance
    // role credentials
    private AuthConfig getAuthConfigFromEC2InstanceRole() throws IOException {
        log.debug("No user and password set for ECR, checking EC2 instance role");
        try (CloseableHttpClient client = HttpClients.custom().useSystemProperties().build()) {
            // we can set very low timeouts because the request returns almost instantly on
            // an EC2 instance
            // on a non-EC2 instance we can fail early
            RequestConfig conf = RequestConfig.custom().setConnectionRequestTimeout(1000).setConnectTimeout(1000)
                    .setSocketTimeout(1000).build();

            // get instance role - if available
            HttpGet request = new HttpGet("http://169.254.169.254/latest/meta-data/iam/security-credentials");
            request.setConfig(conf);
            String instanceRole;
            try (CloseableHttpResponse response = client.execute(request)) {
                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    // no instance role found
                    log.debug("No instance role found, return code was %d", response.getStatusLine().getStatusCode());
                    return null;
                }

                // read instance role
                try (InputStream is = response.getEntity().getContent()) {
                    instanceRole = IOUtils.toString(is, StandardCharsets.UTF_8);
                }
            }
            log.debug("Found instance role %s, getting temporary security credentials", instanceRole);

            // get temporary credentials
            request = new HttpGet("http://169.254.169.254/latest/meta-data/iam/security-credentials/"
                    + UrlEscapers.urlPathSegmentEscaper().escape(instanceRole));
            request.setConfig(conf);
            try (CloseableHttpResponse response = client.execute(request)) {
                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    log.debug("No security credential found, return code was %d",
                            response.getStatusLine().getStatusCode());
                    // no instance role found
                    return null;
                }

                // read instance role
                try (Reader r = new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8)) {
                    JsonObject securityCredentials = new Gson().fromJson(r, JsonObject.class);

                    String user = securityCredentials.getAsJsonPrimitive("AccessKeyId").getAsString();
                    String password = securityCredentials.getAsJsonPrimitive("SecretAccessKey").getAsString();
                    String token = securityCredentials.getAsJsonPrimitive("Token").getAsString();

                    log.debug("Received temporary access key %s...", user.substring(0, 8));
                    return new AuthConfig(user, password, "none", token);
                }
            }
        }
    }

    // if the local credentials don't contain user and password & is not a EC2 instance, use ECS Task instance
    // role credentials
    private AuthConfig getAuthConfigFromECSTaskRole() throws IOException {
        log.debug("No user and password set for ECR, checking ECS Task role");
        try (CloseableHttpClient client = HttpClients.custom().useSystemProperties().build()) {
            RequestConfig conf = RequestConfig.custom().setConnectionRequestTimeout(1000).setConnectTimeout(1000)
                    .setSocketTimeout(1000).build();

            // get ECS task role - if available
            String awsContainerCredentialsUri = System.getenv("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI");
            if (awsContainerCredentialsUri == null) {
                log.debug("System environment not set for variable AWS_CONTAINER_CREDENTIALS_RELATIVE_URI, no task role found");
                return null;
            }

            log.debug("Getting temporary security credentials from: %s", awsContainerCredentialsUri);

            // get temporary credentials
            HttpGet request = new HttpGet("http://169.254.170.2/" + UrlEscapers.urlPathSegmentEscaper().escape(awsContainerCredentialsUri));
            request.setConfig(conf);
            try (CloseableHttpResponse response = client.execute(request)) {
                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    log.debug("No security credential found, return code was %d",
                            response.getStatusLine().getStatusCode());
                    // no task role found
                    return null;
                }

                // read task role
                try (Reader r = new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8)) {
                    JsonObject securityCredentials = new Gson().fromJson(r, JsonObject.class);

                    String user = securityCredentials.getAsJsonPrimitive("AccessKeyId").getAsString();
                    String password = securityCredentials.getAsJsonPrimitive("SecretAccessKey").getAsString();
                    String token = securityCredentials.getAsJsonPrimitive("Token").getAsString();

                    log.debug("Received temporary access key %s...", user.substring(0, 8));
                    return new AuthConfig(user, password, "none", token);
                }
            }
        }
    }

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
                return validateMandatoryOpenShiftLogin(parseOpenShiftConfig(), useOpenAuthModeProp);
            } else {
                return null;
            }
        }

        // Check plugin config
        Map mapToCheck = getAuthConfigMapToCheck(lookupMode,authConfigMap);
        if (mapToCheck != null && mapToCheck.containsKey(AUTH_USE_OPENSHIFT_AUTH) &&
            Boolean.valueOf((String) mapToCheck.get(AUTH_USE_OPENSHIFT_AUTH))) {
                return validateMandatoryOpenShiftLogin(parseOpenShiftConfig(), useOpenAuthModeProp);
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
        JsonObject dockerConfig = DockerFileUtil.readDockerConfig();
        if (dockerConfig == null) {
            return null;
        }
        String registryToLookup = registry != null ? registry : DOCKER_LOGIN_DEFAULT_REGISTRY;

        if (dockerConfig.has("credHelpers") || dockerConfig.has("credsStore")) {
            if (dockerConfig.has("credHelpers")) {
                final JsonObject credHelpers = dockerConfig.getAsJsonObject("credHelpers");
                if (credHelpers.has(registryToLookup)) {
                    return extractAuthConfigFromCredentialsHelper(registryToLookup, credHelpers.get(registryToLookup).getAsString());
                }
            }
            if (dockerConfig.has("credsStore")) {
                return extractAuthConfigFromCredentialsHelper(registryToLookup, dockerConfig.get("credsStore").getAsString());
            }
        }

        if (dockerConfig.has("auths")) {
            return extractAuthConfigFromAuths(registryToLookup, dockerConfig.getAsJsonObject("auths"));
        }

        return null;
    }

    private AuthConfig extractAuthConfigFromAuths(String registryToLookup, JsonObject auths) {
        JsonObject credentials = getCredentialsNode(auths,registryToLookup);
        if (credentials == null || !credentials.has("auth")) {
            return null;
        }
        String auth = credentials.get("auth").getAsString();
        String identityToken = credentials.has("identitytoken") ? credentials.get("identitytoken").getAsString() : null;
        String email = credentials.has(AUTH_EMAIL) ? credentials.get(AUTH_EMAIL).getAsString() : null;
        return new AuthConfig(auth, email, identityToken);
    }

    private AuthConfig extractAuthConfigFromCredentialsHelper(String registryToLookup, String credConfig) throws MojoExecutionException {
        CredentialHelperClient credentialHelper = new CredentialHelperClient(log, credConfig);
        String version = credentialHelper.getVersion();
        log.debug("AuthConfig: credentials from credential helper/store %s%s",
                  credentialHelper.getName(),
                  version != null ? " version " + version : "");
        return credentialHelper.getAuthConfig(registryToLookup);
    }

    private JsonObject getCredentialsNode(JsonObject auths,String registryToLookup) {
        if (auths.has(registryToLookup)) {
            return auths.getAsJsonObject(registryToLookup);
        }
        String registryWithScheme = EnvUtil.ensureRegistryHttpUrl(registryToLookup);
        if (auths.has(registryWithScheme)) {
            return auths.getAsJsonObject(registryWithScheme);
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
        Map kubeConfig = DockerFileUtil.readKubeConfig();
        if (kubeConfig == null) {
            return null;
        }

        String currentContextName = (String) kubeConfig.get("current-context");
        if (currentContextName == null) {
            return null;
        }

        for (Map contextMap : (List<Map>) kubeConfig.get("contexts")) {
            if (currentContextName.equals(contextMap.get("name"))) {
                return parseContext(kubeConfig, (Map) contextMap.get("context"));
            }
        }

        return null;
    }

    private AuthConfig parseContext(Map kubeConfig, Map context) {
        if (context == null) {
            return null;
        }
        String userName = (String) context.get("user");
        if (userName == null) {
            return null;
        }

        List<Map> users = (List<Map>) kubeConfig.get("users");
        if (users == null) {
            return null;
        }

        for (Map userMap : users) {
            if (userName.equals(userMap.get("name"))) {
                return parseUser(userName, (Map) userMap.get("user"));
            }
        }
        return null;
    }

    private AuthConfig parseUser(String userName, Map user) {
        if (user == null) {
            return null;
        }
        String token = (String) user.get("token");
        if (token == null) {
            return null;
        }

        // Strip off stuff after username
        Matcher matcher = Pattern.compile("^([^/]+).*$").matcher(userName);
        return new AuthConfig(matcher.matches() ? matcher.group(1) : userName,
                              token, null, null);
    }

    private AuthConfig validateMandatoryOpenShiftLogin(AuthConfig openShiftAuthConfig, String useOpenAuthModeProp) throws MojoExecutionException {
        if (openShiftAuthConfig != null) {
            return openShiftAuthConfig;
        }
        // No login found
        String kubeConfigEnv = System.getenv("KUBECONFIG");
        throw new MojoExecutionException(
            String.format("System property %s set, but not active user and/or token found in %s. " +
                          "Please use 'oc login' for connecting to OpenShift.",
                          useOpenAuthModeProp, kubeConfigEnv != null ? kubeConfigEnv : "~/.kube/config"));

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
                extractFromServerConfiguration(server.getConfiguration(), AUTH_EMAIL),
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
        REGISTRY("registry.",null),
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
