package io.fabric8.maven.docker.wait;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.fabric8.maven.docker.util.Logger;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpPingCheckerTest {
    @Mock
    private Logger logger;

    private HttpPingChecker httpPingChecker;

    @BeforeEach
    void setup() {
        httpPingChecker = new HttpPingChecker("https://example.com", logger);
    }

    @Test
    void checkingAfterSuccessfulResponseSucceeds() throws IOException {
        try (MockedStatic<HttpClientBuilder> mockedStatic = mockStatic(HttpClientBuilder.class)) {
            final CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
            final HttpClientBuilder httpClientBuilder = mock(HttpClientBuilder.class, RETURNS_SELF);
            when(httpClientBuilder.build()).thenReturn(httpClient);
            mockedStatic.when(HttpClientBuilder::create).thenReturn(httpClientBuilder);

            final CloseableHttpResponse response = mock(CloseableHttpResponse.class);
            when(httpClient.execute(any())).thenReturn(response);

            final StatusLine statusLine = mock(StatusLine.class);
            when(statusLine.getStatusCode()).thenReturn(200);
            when(response.getStatusLine()).thenReturn(statusLine);

            assertTrue(httpPingChecker.check());
        }
    }

    @Test
    void checkingAfterUnsuccessfulResponseFails() throws IOException {
        try (MockedStatic<HttpClientBuilder> mockedStatic = mockStatic(HttpClientBuilder.class)) {
            final CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
            final HttpClientBuilder httpClientBuilder = mock(HttpClientBuilder.class, RETURNS_SELF);
            when(httpClientBuilder.build()).thenReturn(httpClient);
            mockedStatic.when(HttpClientBuilder::create).thenReturn(httpClientBuilder);

            final CloseableHttpResponse response = mock(CloseableHttpResponse.class);
            when(httpClient.execute(any())).thenReturn(response);

            final StatusLine statusLine = mock(StatusLine.class);
            when(statusLine.getStatusCode()).thenReturn(500);
            when(response.getStatusLine()).thenReturn(statusLine);

            assertFalse(httpPingChecker.check());
        }
    }

    @Test
    void checkingAfterIOExceptionFails() throws IOException {
        try (MockedStatic<HttpClientBuilder> mockedStatic = mockStatic(HttpClientBuilder.class)) {
            final CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
            final HttpClientBuilder httpClientBuilder = mock(HttpClientBuilder.class, RETURNS_SELF);
            when(httpClientBuilder.build()).thenReturn(httpClient);
            mockedStatic.when(HttpClientBuilder::create).thenReturn(httpClientBuilder);

            final IOException ioException = mock(IOException.class);
            final String message = "Error " + UUID.randomUUID();
            when(ioException.getMessage()).thenReturn(message);
            when(httpClient.execute(any())).thenThrow(ioException);

            assertFalse(httpPingChecker.check());
            verify(logger).debug(any(), eq(message), any());
        }
    }
}
