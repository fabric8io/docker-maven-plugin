package io.fabric8.maven.docker.access.ecr;

import static org.junit.Assert.*;

import org.junit.Test;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Date;

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.maven.plugin.MojoExecutionException;

public class EcrExtendedAuthTest {

    @Mocked
    private Logger logger;

    @Test
    public void testIsNotAws() {
        assertFalse(new EcrExtendedAuth(logger, "jolokia").isValidRegistry());
    }

    @Test
    public void testIsAws() {
        assertTrue(new EcrExtendedAuth(logger, "123456789012.dkr.ecr.eu-west-1.amazonaws.com").isValidRegistry());
    }

    @Test
    public void testHeaders() {
        EcrExtendedAuth eea = new EcrExtendedAuth(logger, "123456789012.dkr.ecr.eu-west-1.amazonaws.com");
        AuthConfig localCredentials = new AuthConfig("username", "password", null, null);
        HttpPost request = eea.createSignedRequest(localCredentials, new Date(1482009058000L));
        assertEquals("ecr.eu-west-1.amazonaws.com", request.getFirstHeader("host").getValue());
        assertEquals("20161217T211058Z", request.getFirstHeader("X-Amz-Date").getValue());
        assertEquals("AWS4-HMAC-SHA256 Credential=username/20161217/eu-west-1/ecr/aws4_request, SignedHeaders=content-type;host;x-amz-target, Signature=1bab0f5c269debe913e532011d5d192b190bb4c55d3de1bc1506eefb93e058e1", request.getFirstHeader("Authorization").getValue());
    }

    @Test
    public void testClientClosedAndCredentialsDecoded(@Mocked final CloseableHttpClient closeableHttpClient,
            @Mocked final CloseableHttpResponse closeableHttpResponse,
            @Mocked final StatusLine statusLine)
            throws IOException, MojoExecutionException {

        final HttpEntity entity = new StringEntity("{\"authorizationData\": [{"
          +"\"authorizationToken\": \"QVdTOnBhc3N3b3Jk\","
          +"\"expiresAt\": 1448878779.809,"
          +"\"proxyEndpoint\": \"https://012345678910.dkr.ecr.eu-west-1.amazonaws.com\"}]}");

        new Expectations() {{
            statusLine.getStatusCode(); result = 200;
            closeableHttpResponse.getEntity(); result = entity;
        }};
        EcrExtendedAuth eea = new EcrExtendedAuth(logger, "123456789012.dkr.ecr.eu-west-1.amazonaws.com") {
            CloseableHttpClient createClient() {
                return closeableHttpClient;
            }
        };

        AuthConfig localCredentials = new AuthConfig("username", "password", null, null);
        AuthConfig awsCredentials = eea.extendedAuth(localCredentials);
        assertEquals("AWS", awsCredentials.getUsername());
        assertEquals("password", awsCredentials.getPassword());

        new Verifications() {{
             closeableHttpClient.close();
         }};
    }

}
