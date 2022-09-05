package io.fabric8.maven.docker.access.hc;

import io.fabric8.maven.docker.access.chunked.EntityStreamReaderUtil;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
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

    private MockedStatic<EntityStreamReaderUtil> entityStreamReaderUtilMock;

    private Header[] headers;
    private HcChunkedResponseHandlerWrapper hcChunkedResponseHandlerWrapper;
    private InputStream responseInputStream;
    private HttpEntity entity;

    @BeforeEach
    void setUp() {
        responseInputStream = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
        entity = new InputStreamEntity(responseInputStream);
        hcChunkedResponseHandlerWrapper = new HcChunkedResponseHandlerWrapper(handler);
        entityStreamReaderUtilMock = Mockito.mockStatic(EntityStreamReaderUtil.class);
        entityStreamReaderUtilMock.when(() -> EntityStreamReaderUtil.processJsonStream(Mockito.any(), Mockito.any())).thenCallRealMethod();
    }

    @AfterEach
    void tearDown() {
        entityStreamReaderUtilMock.close();
    }

    @Test
    void handleResponseWithJsonResponse() throws IOException {
        givenResponseHeaders(new BasicHeader("ConTenT-Type", "application/json; charset=UTF-8"));
        hcChunkedResponseHandlerWrapper.handleResponse(response);
        verifyProcessJsonStream(1);
        verifyResponseUnbuffered();
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
        verifyResponseBuffered();
    }

    private void givenResponseHeaders(Header... headers) throws IOException {
        Mockito.doReturn(headers).when(response).getAllHeaders();
        Mockito.doReturn(entity).when(response).getEntity();
    }

    private void verifyProcessJsonStream(int timesCalled) throws IOException {
        Mockito.verify(handler, Mockito.times(timesCalled)).stop();
    }

    // Response is unbuffered when processJsonStream() called on original stream
    private void verifyResponseUnbuffered() {
        entityStreamReaderUtilMock.verify(() -> EntityStreamReaderUtil.processJsonStream(Mockito.eq(handler), Mockito.eq(responseInputStream)));
    }

    // Response is buffered when processJsonStream() not called on original stream
    private void verifyResponseBuffered() {
        entityStreamReaderUtilMock.verify(() -> EntityStreamReaderUtil.processJsonStream(Mockito.eq(handler), AdditionalMatchers.not(Mockito.eq(responseInputStream))));
    }
}
