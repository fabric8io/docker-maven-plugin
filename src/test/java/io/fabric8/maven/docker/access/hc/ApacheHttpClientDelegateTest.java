package io.fabric8.maven.docker.access.hc;

import com.google.common.collect.ImmutableMap;
import io.fabric8.maven.docker.access.hc.ApacheHttpClientDelegate.StatusCodeCheckerResponseHandler;
import io.fabric8.maven.docker.access.hc.util.ClientBuilder;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@SuppressWarnings({"rawtypes", "unused"})
@ExtendWith(MockitoExtension.class)
class ApacheHttpClientDelegateTest {

    @Mock
    private ClientBuilder clientBuilder;
    @Mock
    private CloseableHttpClient httpClient;

    private ApacheHttpClientDelegate apacheHttpClientDelegate;

    @BeforeEach
    void setUp() throws IOException {
        Mockito.doReturn(httpClient).when(clientBuilder).buildBasicClient();
        apacheHttpClientDelegate = new ApacheHttpClientDelegate(clientBuilder, false);
    }

    @Test
    void createBasicClient() {
        final CloseableHttpClient result = apacheHttpClientDelegate.createBasicClient();
        Assertions.assertNotNull(result);
    }

    @Test
    void delete() throws IOException {
        // Given
        Mockito.doReturn(1337)
            .when(httpClient)
            .execute(Mockito.any(HttpUriRequest.class), Mockito.any(ResponseHandler.class));
        // When
        final int result = apacheHttpClientDelegate.delete("http://example.com");
        // Then
        Assertions.assertEquals(1337, result);
        verifyHttpClientExecute((request, responseHandler) ->
            Assertions.assertEquals(Collections.singletonMap("Accept", "*/*"), headersAsMap(request.getAllHeaders()))
        );
    }

    private static Map<String,String> headersAsMap(Header[] headers) {
       return Arrays.stream(headers).collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
    }

    @Test
    void get() throws IOException {
        // Given
        Mockito.doReturn("Response")
            .when(httpClient)
            .execute(Mockito.any(HttpUriRequest.class), Mockito.any(ResponseHandler.class));
        // When
        final String response = apacheHttpClientDelegate.get("http://example.com");
        // Then
        Assertions.assertEquals("Response", response);
        verifyHttpClientExecute((HttpUriRequest request, ResponseHandler responseHandler) -> {
            Assertions.assertEquals(Collections.singletonMap("Accept", "*/*"), headersAsMap(request.getAllHeaders()));
            StatusCodeCheckerResponseHandler statusCodeChecker = (StatusCodeCheckerResponseHandler) responseHandler;
            Assertions.assertTrue(statusCodeChecker.delegate instanceof ApacheHttpClientDelegate.BodyResponseHandler);
            Assertions.assertArrayEquals(new int[]{}, statusCodeChecker.statusCodes);
        });
    }

    @Test
    void postWithStringBody() throws IOException {
        // Given
        Mockito.doReturn("Response")
            .when(httpClient)
            .execute(Mockito.any(HttpUriRequest.class), Mockito.any(ResponseHandler.class));
        // When
        final String response = apacheHttpClientDelegate.post(
                "http://example.com", "{body}", Collections.singletonMap("EXTRA", "HEADER"), null);
        // Then
        Assertions.assertEquals("Response", response);
        
        verifyHttpClientExecute((request, responseHandler) ->
            Assertions.assertEquals(ImmutableMap.of("Accept", "*/*", "Content-Type", "application/json", "EXTRA", "HEADER"),
                headersAsMap(request.getAllHeaders()))
        );
    }

    @Test
    void postWithFileBody() throws IOException {
        // Given
        Mockito.doReturn("Response")
            .when(httpClient)
            .execute(Mockito.any(HttpUriRequest.class), Mockito.any(ResponseHandler.class));
        // When
        final String response = apacheHttpClientDelegate.post(
                "http://example.com", new File("fake-file.tar"), null);
        // Then
        Assertions.assertEquals("Response", response);

        verifyHttpClientExecute((request, responseHandler) ->
            Assertions.assertEquals(ImmutableMap.of("Accept", "*/*", "Content-Type", "application/x-tar"),
                headersAsMap(request.getAllHeaders()))
        );
    }

    private <H extends ResponseHandler> void verifyHttpClientExecute(BiConsumer<HttpUriRequest, H> consumer) throws IOException {
        ArgumentCaptor<HttpUriRequest> requestCaptor = ArgumentCaptor.forClass(HttpUriRequest.class);
        ArgumentCaptor<ResponseHandler> responseHandlerCaptor = ArgumentCaptor.forClass(ResponseHandler.class);
        Mockito.verify(httpClient)
            .execute(requestCaptor.capture(), responseHandlerCaptor.capture());
        consumer.accept(requestCaptor.getValue(), (H)responseHandlerCaptor.getValue());
    }

}
