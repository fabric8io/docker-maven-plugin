package io.fabric8.maven.docker.access.ecr;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.maven.plugin.MojoExecutionException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.util.Logger;

/**
 * Exchange local stored credentials for temporary ecr credentials
 *
 * @author chas
 * @since 2016-12-9
 */
public class EcrExtendedAuth {

    private static final Pattern AWS_REGISTRY =
            Pattern.compile("^(\\d{12})\\.dkr\\.ecr\\.([a-z\\-0-9]+)\\.amazonaws\\.com$");

    private final Logger logger;
    private final Matcher matcher;

    /**
     * Initialize an extended authentication for ecr registry.
     *
     * @param registry The registry, we may or may not be an ecr registry.
     */
    public EcrExtendedAuth(Logger logger, String registry) {
        this.logger = logger;
        matcher = AWS_REGISTRY.matcher(registry);
        logger.debug("registry = %s", registry);
    }

    /**
     * Is the registry an ecr registry?
     * @return true, if the registry matches the ecr pattern
     */
    public boolean isValidRegistry() {
        boolean isValid = matcher.matches();
        logger.debug("isValidRegistry %b", isValid);
        return isValid;
    }

    /**
     * Perform extended authentication.  Use the provided credentials as IAM credentials and
     * get a temporary ECR token.
     *
     * @param localCredentials IAM id/secret
     * @return ECR base64 encoded username:password
     * @throws IOException
     * @throws MojoExecutionException
     */
    public AuthConfig extendedAuth(AuthConfig localCredentials) throws IOException, MojoExecutionException {
        JSONObject jo = getAuthorizationToken(localCredentials);

        JSONArray authorizationDatas = jo.getJSONArray("authorizationData");
        JSONObject authorizationData = authorizationDatas.getJSONObject(0);
        String authorizationToken = authorizationData.getString("authorizationToken");

        return new AuthConfig(authorizationToken, "none");
    }

    private JSONObject getAuthorizationToken(AuthConfig localCredentials) throws IOException, MojoExecutionException {
        String accountId = matcher.group(1);
        String region = matcher.group(2);
        String host = "ecr." + region + ".amazonaws.com";

        logger.debug("GetAuthorizationToken from %s", host);

        HttpPost request = new HttpPost("https://" + host + '/');
        request.setHeader("host", host);
        request.setHeader("Content-Type", "application/x-amz-json-1.1");
        request.setHeader("X-Amz-Target", "AmazonEC2ContainerRegistry_V20150921.GetAuthorizationToken");
        request.setEntity(new StringEntity("{\"registryIds\":[\""+ accountId + "\"]}", StandardCharsets.UTF_8));

        AwsSigner4 signer = new AwsSigner4(region, "ecr");
        signer.sign(request, localCredentials);

        CloseableHttpClient client = HttpClients.createDefault();
        try {
            CloseableHttpResponse response = client.execute(request);
            logger.debug("Response status %d", response.getStatusLine().getStatusCode());
            if(response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new MojoExecutionException("AWS authentication failure");
            }

            HttpEntity entity = response.getEntity();
            Reader jr = new InputStreamReader(entity.getContent(), StandardCharsets.UTF_8);
            return new JSONObject(new JSONTokener(jr));
        }
        finally {
            client.close();
        }
    }
}
