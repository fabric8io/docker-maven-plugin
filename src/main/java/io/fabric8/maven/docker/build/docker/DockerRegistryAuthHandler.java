package io.fabric8.maven.docker.build.docker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.Optional;
import java.util.function.Function;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.fabric8.maven.docker.build.auth.RegistryAuth;
import io.fabric8.maven.docker.build.auth.RegistryAuthConfig;
import io.fabric8.maven.docker.build.auth.RegistryAuthHandler;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.Logger;

/**
 * @author roland
 * @since 21.10.18
 */
public class DockerRegistryAuthHandler implements RegistryAuthHandler {

    static final String DOCKER_LOGIN_DEFAULT_REGISTRY = "https://index.docker.io/v1/";

    private final Logger log;
    private final Gson gson;

    public DockerRegistryAuthHandler(Logger log) {
        this.log = log;
        this.gson = new Gson();

    }

    @Override
    public String getId() {
        return "docker";
    }

    @Override
    public RegistryAuth create(RegistryAuthConfig.Kind kind, String user, String registry, Function<String, String> decryptor) {
        return readDockerConfig().map(d -> extractAuthConfigFromDocker(d, registry)).orElse(null);
    }

    private RegistryAuth extractAuthConfigFromDocker(JsonObject dockerConfig, String registry) {
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

    private Optional<JsonObject> readDockerConfig() {
        String dockerConfig = System.getenv("DOCKER_CONFIG");

        Optional<Reader> reader = dockerConfig == null
            ? getFileReaderFromDir(new File(getHomeDir(), ".docker/config.json"))
            : getFileReaderFromDir(new File(dockerConfig, "config.json"));
        return reader.map(r -> gson.fromJson(r, JsonObject.class));
    }

    private RegistryAuth extractAuthConfigFromAuths(String registryToLookup, JsonObject auths) {
        JsonObject credentials = getCredentialsNode(auths, registryToLookup);
        if (credentials == null || !credentials.has("auth")) {
            return null;
        }
        String auth = credentials.get("auth").getAsString();
        String email = credentials.has("email") ? credentials.get("email").getAsString() : null;
        return new RegistryAuth.Builder().withCredentialsEncoded(auth).email(email).build();
    }

    private RegistryAuth extractAuthConfigFromCredentialsHelper(String registryToLookup, String credConfig) {
        CredentialHelperClient credentialHelper = new CredentialHelperClient(log, credConfig);
        log.debug("AuthConfig: credentials from credential helper/store %s version %s",
                  credentialHelper.getName(),
                  credentialHelper.getVersion());
        return credentialHelper.getAuthConfig(registryToLookup);
    }

    private JsonObject getCredentialsNode(JsonObject auths, String registryToLookup) {
        if (auths.has(registryToLookup)) {
            return auths.getAsJsonObject(registryToLookup);
        }
        String registryWithScheme = EnvUtil.ensureRegistryHttpUrl(registryToLookup);
        if (auths.has(registryWithScheme)) {
            return auths.getAsJsonObject(registryWithScheme);
        }
        return null;
    }

    private Optional<Reader> getFileReaderFromDir(File file) {
        try {
            return Optional.of(new FileReader(file));
        } catch (FileNotFoundException e) {
            return Optional.empty();
        }
    }

    private File getHomeDir() {
        String homeDir = Optional.ofNullable(System.getProperty("user.home")).orElse(System.getenv("HOME"));
        return new File(homeDir);
    }


}
