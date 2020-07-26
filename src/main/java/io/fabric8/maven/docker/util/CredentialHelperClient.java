package io.fabric8.maven.docker.util;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;

import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.util.List;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.util.ExternalCommand;

public class CredentialHelperClient {

    static final String SECRET_KEY = "Secret";
    static final String USERNAME_KEY = "Username";
    private final String credentialHelperName;
    private final Logger log;

    public CredentialHelperClient(Logger log, String credentialsStore) {
        this.log = log;
        credentialHelperName = "docker-credential-" + credentialsStore;
    }

    public String getName() {
        return credentialHelperName;
    }

    public String getVersion() {
        try {
            return new VersionCommand().getVersion();
        } catch (IOException e) {
            return null;
        }
    }

    public AuthConfig getAuthConfig(String registryToLookup) throws MojoExecutionException {
        try {
            JsonObject creds = new GetCommand().getCredentialNode(registryToLookup);
            if (creds == null) {
                creds = new GetCommand().getCredentialNode(EnvUtil.ensureRegistryHttpUrl(registryToLookup));
            }
            return toAuthConfig(creds);
        } catch (IOException e) {
            throw new MojoExecutionException("Error getting the credentials for " + registryToLookup + " from the configured credential helper",e);
        }
    }

    private AuthConfig toAuthConfig(JsonObject credential){
        if (credential == null) {
            return null;
        }
        String password = credential.get(CredentialHelperClient.SECRET_KEY).getAsString();
        String userKey = credential.get(CredentialHelperClient.USERNAME_KEY).getAsString();
        return new AuthConfig(userKey,password, null,null);
    }

    // docker-credential-XXX version
    private class VersionCommand extends ExternalCommand {

        private String version;

        VersionCommand() {
            super(CredentialHelperClient.this.log);
        }

        @Override
        protected String[] getArgs() {
            return new String[]{CredentialHelperClient.this.credentialHelperName,"version"};
        }

        @Override
        protected void processLine(String line) {
            log.verbose(Logger.LogVerboseCategory.BUILD,"Credentials helper reply for \"%s\" is %s",CredentialHelperClient.this.credentialHelperName,line);
            version = line;
        }

        public String getVersion() throws IOException {
            execute();
            return version;
        }
    }

    // echo <registryToLookup> | docker-credential-XXX get
    private class GetCommand extends ExternalCommand {

        private List<String> reply = Lists.newLinkedList();

        GetCommand() {
            super(CredentialHelperClient.this.log);
        }

        @Override
        protected String[] getArgs() {
            return new String[]{CredentialHelperClient.this.credentialHelperName,"get"};
        }

        @Override
        protected void processLine(String line) {
            reply.add(line);
        }

        public JsonObject getCredentialNode(String registryToLookup) throws IOException {
            try {
                execute(registryToLookup);
            } catch (IOException ex) {
                if (getStatusCode() == 1) {
                    return null;
                } else {
                    throw ex;
                }
            }
            JsonObject credentials = JsonFactory.newJsonObject(Joiner.on('\n').join(reply));
            if (!credentials.has(SECRET_KEY) || !credentials.has(USERNAME_KEY)) {
                return null;
            }
            return credentials;
        }
    }
}
