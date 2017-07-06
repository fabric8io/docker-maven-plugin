package io.fabric8.maven.docker.util;

import io.fabric8.maven.docker.access.util.ExternalCommand;
import org.apache.maven.plugin.MojoExecutionException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;

public class CredentialHelperClient {

    static final String SECRET_KEY = "Secret";
    static final String USERNAME_KEY = "Username";
    private final String credentialHelperName;
    private final Logger log;

    public CredentialHelperClient(Logger log,String credentialsStore) {
        this.log = log;
        credentialHelperName = "docker-credential-" + credentialsStore;
    }

    public String getName() {
        return credentialHelperName;
    }

    public String getVersion() throws MojoExecutionException {
        try {
            return new VersionCommand().getVersion();
        } catch (IOException e) {
            throw new MojoExecutionException("Error getting the version of the configured credential helper",e);
        }
    }

    public JSONObject getCredentialNode(String registryToLookup) throws MojoExecutionException {
        try {
            final GetCommand getCommand = new GetCommand();
            final JSONObject credential = getCommand.getCredentialNode(registryToLookup);
            if (credential != null) {
                return credential;
            }
            return getCommand.getCredentialNode("https://" + registryToLookup);
        } catch (IOException e) {
            throw new MojoExecutionException("Error getting the credentials for " + registryToLookup + " from the configured credential helper",e);
        }
    }

    // docker-credential-XXX reply
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
            log.info("Credentials helper reply for \"%s\" is %s",CredentialHelperClient.this.credentialHelperName,line);
            version = line;
        }

        public String getVersion() throws IOException {
            execute();
            if (version == null) {
                throw new IOException("No reply information returned by " + getCommandAsString());
            }
            return version;
        }
    }

    // echo <registryToLookup> | docker-credential-XXX get
    private class GetCommand extends ExternalCommand {

        private String reply;

        GetCommand() {
            super(CredentialHelperClient.this.log);
        }

        @Override
        protected String[] getArgs() {
            return new String[]{CredentialHelperClient.this.credentialHelperName,"get"};
        }

        @Override
        protected void processLine(String line) {
            reply = line;
        }

        public JSONObject getCredentialNode(String registryToLookup) throws IOException {
            try {
                execute(registryToLookup);
            } catch (IOException ex) {
                if (getStatusCode() == 1) {
                    return null;
                } else {
                    throw ex;
                }
            }
            JSONObject credentials = new JSONObject(new JSONTokener(reply));
            if (!credentials.has(SECRET_KEY) || !credentials.has(USERNAME_KEY)) {
                return null;
            }
            return credentials;
        }
    }
}
