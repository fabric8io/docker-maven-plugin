package io.fabric8.maven.docker.build.auth;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.fabric8.maven.docker.build.auth.AuthConfigHandler.LookupMode;
import io.fabric8.maven.docker.build.auth.handler.DockerAuthConfigHandler;
import io.fabric8.maven.docker.build.auth.extended.EcrExtendedAuthConfigHandler;
import io.fabric8.maven.docker.build.auth.handler.OpenShiftAuthConfigHandler;
import io.fabric8.maven.docker.build.auth.handler.PluginConfigAuthConfigHandler;
import io.fabric8.maven.docker.build.auth.handler.SettingsAuthConfigHandler;
import io.fabric8.maven.docker.build.auth.handler.SystemPropertyAuthConfigHandler;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import static io.fabric8.maven.docker.build.auth.AuthConfigHandler.LookupMode.DEFAULT;
import static io.fabric8.maven.docker.build.auth.AuthConfigHandler.LookupMode.PULL;
import static io.fabric8.maven.docker.build.auth.AuthConfigHandler.LookupMode.PUSH;


/**
 * Factory for creating docker specific authentication configuration
 *
 * @author roland
 * @since 29.07.14
 */
public class AuthConfigFactory {

    public static final String SKIP_EXTENDED_AUTH = "skipExtendedAuth";

    private final PlexusContainer container;
    private final Map registryAuthConfig;
    private final String defaultRegistry;

    private Logger log;
    private List<AuthConfigHandler> authConfigHandlers;
    private List<AuthConfigHandler.Extender> extendedAuthConfigHandlers;
    private AuthConfigHandler fallbackAuthHandler;

    /**
     * Constructor which should be used during startup phase of a plugin
     *
     * @param container the container used for do decryption of passwords
     */
    public AuthConfigFactory(PlexusContainer container, Settings settings, Map registryAuthConfig, String defaultRegistry, Logger log) {
        this.container = container;
        this.registryAuthConfig = registryAuthConfig;
        this.defaultRegistry = defaultRegistry;
        this.log = log;

        initAuthConfigHandler(settings);
    }

    private void initAuthConfigHandler(Settings settings) {
        authConfigHandlers = new ArrayList<>();
        authConfigHandlers.addAll(Arrays.asList(
            new SystemPropertyAuthConfigHandler(log),
            new OpenShiftAuthConfigHandler(registryAuthConfig, log),
            new PluginConfigAuthConfigHandler(registryAuthConfig, log),
            new SettingsAuthConfigHandler(settings, log),
            new DockerAuthConfigHandler(log)
                                               ));

        extendedAuthConfigHandlers = new ArrayList<>();
        extendedAuthConfigHandlers.addAll(Arrays.asList(
            new EcrExtendedAuthConfigHandler(log)
                                                       ));
    }

    /**
     * Create an authentication config object which can be used for communication with a Docker registry
     * <p>
     * The authentication information is looked up at various places (in this order):
     *
     * <ul>
     * <li>From system properties</li>
     * <li>From the provided map which can contain key-value pairs</li>
     * <li>From the openshift settings in ~/.config/kube</li>
     * <li>From the Maven settings stored typically in ~/.m2/settings.xml</li>
     * <li>From the Docker settings stored in ~/.docker/config.json</li>
     * </ul>
     * <p>
     * The following properties (prefix with 'docker.') and config key are evaluated:
     *
     * <ul>
     * <li>username: User to authenticate</li>
     * <li>password: Password to authenticate. Can be encrypted</li>
     * <li>email: Optional EMail address which is send to the registry, too</li>
     * </ul>
     * <p>
     * If the repository is in an aws ecr registry and skipExtendedAuth is not true, if found
     * credentials are not from docker settings, they will be interpreted as iam credentials
     * and exchanged for ecr credentials.
     *
     * @param isPush           if true this AuthConfig is created for a push, if false it's for a pull
     * @param user             user to check for
     * @param specificRegistry registry to use, might be null in which case a default registry is checked,
     * @return the authentication configuration or <code>EMPTY_AUTH_CONFIG</code> if none could be found
     */
    public AuthConfig createAuthConfig(boolean isPush, String user, String specificRegistry) throws IOException {

        String registry = specificRegistry != null ? specificRegistry : defaultRegistry;
        AuthConfig ret = createStandardAuthConfig(isPush, user, registry);

        if (ret != null) {
            if (registry == null || Boolean.TRUE.equals(registryAuthConfig.get(SKIP_EXTENDED_AUTH))) {
                return ret;
            }
            AuthConfig extendedRet = extendAuthConfig(registry, ret);
            return extendedRet != null ? extendedRet : ret;
        }

        log.debug("AuthConfig: no credentials found");
        return AuthConfig.EMPTY_AUTH_CONFIG;
    }

    private AuthConfig extendAuthConfig(String registry, AuthConfig ret) throws IOException {
        for (AuthConfigHandler.Extender extended : extendedAuthConfigHandlers) {
            AuthConfig extendedRet = extended.extend(ret, registry);
            if (extendedRet != null) {
                return extendedRet;
            }
        }
        return null;
    }


    private AuthConfig createStandardAuthConfig(boolean isPush, String user, String registry) {

        for (LookupMode lookupMode : new LookupMode[]{isPush ? PUSH : PULL, DEFAULT}) {
            for (AuthConfigHandler handler : authConfigHandlers) {
                AuthConfig ret = handler.create(lookupMode, user, registry, this::decrypt);
                if (ret != null) {
                    return ret;
                }
            }
        }

        // No authentication found
        return null;
    }


    // =======================================================================================================


    private String decrypt(String password) {
        try {
            // Done by reflection since I have classloader issues otherwise
            Object secDispatcher = container.lookup(SecDispatcher.ROLE, "maven");
            Method method = secDispatcher.getClass().getMethod("decrypt", String.class);
            return (String) method.invoke(secDispatcher, password);
        } catch (ComponentLookupException e) {
            throw new IllegalStateException("Error looking security dispatcher", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot decrypt password: " + e.getCause(), e);
        }
    }

    // ========================================================================================
    // Mode which direction to lookup (pull, push or default value for both, pull and push)

}
