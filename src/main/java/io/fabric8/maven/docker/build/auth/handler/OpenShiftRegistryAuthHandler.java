package io.fabric8.maven.docker.build.auth.handler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.fabric8.maven.docker.build.auth.RegistryAuth;
import io.fabric8.maven.docker.build.auth.RegistryAuthConfig;
import io.fabric8.maven.docker.build.auth.RegistryAuthHandler;
import io.fabric8.maven.docker.util.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * @author roland
 * @since 21.10.18
 */
public class OpenShiftRegistryAuthHandler implements RegistryAuthHandler {

    public static final String AUTH_USE_OPENSHIFT_AUTH = "useOpenShiftAuth";

    private final RegistryAuthConfig registryAuthConfig;
    private final Logger log;

    public OpenShiftRegistryAuthHandler(RegistryAuthConfig registryAuthConfig, Logger log) {
        this.registryAuthConfig = registryAuthConfig;
        this.log = log;
    }

    @Override
    public String getId() {
        return "openshift";
    }

    @Override
    public RegistryAuth create(RegistryAuthConfig.Kind kind, String user, String registry, Function<String, String> decryptor) {
        // Check for openshift authentication either from the plugin config or from system props
        Properties props = System.getProperties();
        String useOpenAuthMode = registryAuthConfig.extractFromProperties(props, kind, AUTH_USE_OPENSHIFT_AUTH);
        // Check for system property
        if (useOpenAuthMode != null) {
            boolean useOpenShift = Boolean.valueOf(useOpenAuthMode);
            if (!useOpenShift) {
                return null;
            }
            log.debug("AuthConfig: OpenShift credentials");
            return validateMandatoryOpenShiftLogin(parseOpenShiftConfig());
        }

        boolean useOpenShiftAuth =
            Optional.ofNullable(registryAuthConfig.getConfigForHandler(getId(),AUTH_USE_OPENSHIFT_AUTH))
                    .map(Boolean::parseBoolean)
                    .orElse(false);
        if (useOpenShiftAuth) {
            log.debug("AuthConfig: OpenShift credentials");
            return validateMandatoryOpenShiftLogin(parseOpenShiftConfig());
        }

        return null;
    }

    private RegistryAuth validateMandatoryOpenShiftLogin(RegistryAuth openShiftRegistryAuth) {
        if (openShiftRegistryAuth != null) {
            return openShiftRegistryAuth;
        }
        // No login found
        String kubeConfigEnv = System.getenv("KUBECONFIG");
        throw new IllegalArgumentException(
            String.format("OpenShift auth check enabled, but not active user and/or token found in %s. " +
                          "Please use 'oc login' for connecting to OpenShift.", kubeConfigEnv != null ? kubeConfigEnv : "~/.kube/config"));

    }

    // Parse OpenShift config to get credentials, but return null if not found
    private RegistryAuth parseOpenShiftConfig() {
        Map kubeConfig = readKubeConfig();
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

    private Map<String, ?> readKubeConfig() {
        String kubeConfig = System.getenv("KUBECONFIG");
        Optional<Reader> reader =
            getFileReaderFromDir(kubeConfig == null ? new File(getHomeDir(), ".kube/config") : new File(kubeConfig));

        return (Map<String, ?>) reader.map(r -> new Yaml().load(r)).orElse(null);
    }

    private Optional<Reader> getFileReaderFromDir(File file) {
        try {
            return Optional.of(new FileReader(file));
        } catch (FileNotFoundException e) {
            return Optional.empty();
        }
    }

    private File getHomeDir() {
        String homeDir = System.getProperty("user.home") != null ? System.getProperty("user.home") : System.getenv("HOME");
        return new File(homeDir);
    }

    private RegistryAuth parseContext(Map kubeConfig, Map context) {
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

    private RegistryAuth parseUser(String userName, Map user) {
        if (user == null) {
            return null;
        }
        String token = (String) user.get("token");
        if (token == null) {
            return null;
        }

        // Strip off stuff after username
        Matcher matcher = Pattern.compile("^([^/]+).*$").matcher(userName);
        return new RegistryAuth.Builder()
            .username(matcher.matches() ? matcher.group(1) : userName)
            .password(token)
            .build();
    }
}
