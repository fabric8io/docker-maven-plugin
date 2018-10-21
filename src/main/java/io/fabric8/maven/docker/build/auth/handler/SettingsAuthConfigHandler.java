package io.fabric8.maven.docker.build.auth.handler;

import io.fabric8.maven.docker.build.auth.AuthConfig;
import io.fabric8.maven.docker.build.auth.AuthConfigHandler;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * @author roland
 * @since 21.10.18
 */
public class SettingsAuthConfigHandler implements AuthConfigHandler {

    private static final String[] DEFAULT_REGISTRIES = new String[]{
        "docker.io", "index.docker.io", "registry.hub.docker.com"
    };

    private final Settings settings;
    private final Logger log;

    public SettingsAuthConfigHandler(Settings settings, Logger log) {
        this.settings = settings;
        this.log = log;
    }

    @Override
    public AuthConfig create(LookupMode mode, String user, String registry, Decryptor decryptor) {
        // Now lets lookup the registry & user from ~/.m2/setting.xml
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
                log.debug("AuthConfig: credentials from ~/.m2/setting.xml (%s)", found);
                return createAuthConfigFromServer(found, decryptor);
            }
        }

        if (defaultServer != null) {
            log.debug("AuthConfig: credentials from ~/.m2/setting.xml (%s)", defaultServer);
            return createAuthConfigFromServer(defaultServer, decryptor);
        }

        return null;
    }

    private Server checkForServer(Server server, String id, String registry, String user) {

        String[] registries = registry != null ? new String[]{registry} : DEFAULT_REGISTRIES;
        for (String reg : registries) {
            if (id.equals(user == null ? reg : reg + "/" + user)) {
                return server;
            }
        }
        return null;
    }

    private AuthConfig createAuthConfigFromServer(Server server, Decryptor decryptor) {
        return new AuthConfig(
            server.getUsername(),
            decryptor.decrypt(server.getPassword()),
            extractFromServerConfiguration(server.getConfiguration(), AuthConfig.AUTH_EMAIL),
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
