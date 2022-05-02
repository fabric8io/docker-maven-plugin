package io.fabric8.maven.docker.access.ecr;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.util.Logger;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;

/**
 * Test exchange of local stored credentials for temporary ecr credentials
 *
 * @author chas
 * @since 2016-12-21
 */
@ExtendWith(MockitoExtension.class)
class EcrExtendedAuthTest {

    @Mock
    private Logger logger;

    @Test
    void testIsNotAws() {
        Assertions.assertFalse(new EcrExtendedAuth(logger, "jolokia").isAwsRegistry());
    }

    @Test
    void testIsAws() {
        Assertions.assertTrue(new EcrExtendedAuth(logger, "123456789012.dkr.ecr.eu-west-1.amazonaws.com").isAwsRegistry());
    }

    @Test
    void testHeaders() throws ParseException {
        EcrExtendedAuth eea = new EcrExtendedAuth(logger, "123456789012.dkr.ecr.eu-west-1.amazonaws.com");
        AuthConfig localCredentials = new AuthConfig("username", "password", null, null);
        Date signingTime = AwsSigner4Request.TIME_FORMAT.parse("20161217T211058Z");
        HttpPost request = eea.createSignedRequest(localCredentials, signingTime);
        Assertions.assertEquals("api.ecr.eu-west-1.amazonaws.com", request.getFirstHeader("host").getValue());
        Assertions.assertEquals("20161217T211058Z", request.getFirstHeader("X-Amz-Date").getValue());
        Assertions.assertEquals("AWS4-HMAC-SHA256 Credential=username/20161217/eu-west-1/ecr/aws4_request, SignedHeaders=content-type;host;x-amz-target, Signature=2ae11d499499cc951900aac0fbec96009382ba4f735bd14baa375c3e51d50aa9", request.getFirstHeader("Authorization").getValue());
    }

    @Test
    void testClientClosedAndCredentialsDecoded(@Mock final CloseableHttpClient closeableHttpClient,
            @Mock final CloseableHttpResponse closeableHttpResponse,
            @Mock final StatusLine statusLine)
            throws IOException, MojoExecutionException {

        final HttpEntity entity = new StringEntity("{\"authorizationData\": [{"
          +"\"authorizationToken\": \"QVdTOnBhc3N3b3Jk\","
          +"\"expiresAt\": 1448878779.809,"
          +"\"proxyEndpoint\": \"https://012345678910.dkr.ecr.eu-west-1.amazonaws.com\"}]}");

        Mockito.doReturn(closeableHttpResponse).when(closeableHttpClient).execute(Mockito.any(HttpUriRequest.class));
        Mockito.doReturn(statusLine).when(closeableHttpResponse).getStatusLine();
        Mockito.doReturn(200).when(statusLine).getStatusCode();
        Mockito.doReturn(entity).when(closeableHttpResponse).getEntity();

        EcrExtendedAuth eea = new EcrExtendedAuth(logger, "123456789012.dkr.ecr.eu-west-1.amazonaws.com") {
            CloseableHttpClient createClient() {
                return closeableHttpClient;
            }
        };

        AuthConfig localCredentials = new AuthConfig("username", "password", null, null);
        AuthConfig awsCredentials = eea.extendedAuth(localCredentials);
        Assertions.assertEquals("AWS", awsCredentials.getUsername());
        Assertions.assertEquals("password", awsCredentials.getPassword());

        Mockito.verify(closeableHttpClient).close();
    }

}
