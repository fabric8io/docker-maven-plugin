package io.fabric8.maven.docker.access.hc;

import io.fabric8.maven.docker.access.hc.util.ClientBuilder;
import io.fabric8.maven.docker.util.Logger;
import mockit.Mocked;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class DockerAccessWithHcClientSSLTest {

    private DockerAccessWithHcClient client;

    @Mocked
    private ApacheHttpClientDelegate mockDelegate;

    @Mocked
    private Logger mockLogger;

    @Before
    public void setup() throws IOException {
        client = new DockerAccessWithHcClient("v1.20", "https://1.2.3.4:2376", null, true, 1, mockLogger) {
            @Override
            ApacheHttpClientDelegate createHttpClient(ClientBuilder builder) throws IOException {
                return mockDelegate;
            }
        };
    }

    @Test
    public void testPushFailes_noRetry() throws Exception {
        assertEquals(this.client.getUrlBuilder().getBaseUrl(), "http://1.2.3.4:2376");
    }
}
