package io.fabric8.maven.docker.access.hc;

import io.fabric8.maven.docker.access.chunked.EntityStreamReaderUtil;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;

@SuppressWarnings("unused")
@ExtendWith(MockitoExtension.class)
class HcChunkedResponseHandlerWrapperTest {

    @Mock
    private EntityStreamReaderUtil.JsonEntityResponseHandler handler;
    @Mock
    private HttpResponse response;
    @Mock
    private HttpEntity entity;
    @Mock
    private EntityStreamReaderUtil entityStreamReaderUtil;

    private Header[] headers;
    private HcChunkedResponseHandlerWrapper hcChunkedResponseHandlerWrapper;

    @BeforeEach
    void setUp() {
        hcChunkedResponseHandlerWrapper = new HcChunkedResponseHandlerWrapper(handler);
    }

    @Test
    void handleResponseWithJsonResponse() throws IOException {
        givenResponseHeaders(new BasicHeader("ConTenT-Type", "application/json; charset=UTF-8"));
        hcChunkedResponseHandlerWrapper.handleResponse(response);
        verifyProcessJsonStream(1);
    }

    @Test
    void handleResponseWithTextPlainResponse() throws IOException {
        givenResponseHeaders(new BasicHeader("Content-Type", "text/plain"));
        hcChunkedResponseHandlerWrapper.handleResponse(response);
        verifyProcessJsonStream(0);
    }

    @Test
    void handleResponseWithNoContentType() throws IOException {
        givenResponseHeaders();
        hcChunkedResponseHandlerWrapper.handleResponse(response);
        // timesCalled is 1 here because without "Content-Type" handleResponse() tries to parse the body to
        // detect if it is JSON or not. See HcChunkedResponseHandlerWrapper.handleResponse() for more details.
        verifyProcessJsonStream(1);
    }

    private void givenResponseHeaders(Header... headers) throws IOException {
        Mockito.doReturn(headers).when(response).getAllHeaders();
        Mockito.doReturn(new StringEntity("{}")).when(response).getEntity();
    }

    private void verifyProcessJsonStream(int timesCalled) throws IOException {
        Mockito.verify(handler, Mockito.times(timesCalled)).stop();
    }
}
