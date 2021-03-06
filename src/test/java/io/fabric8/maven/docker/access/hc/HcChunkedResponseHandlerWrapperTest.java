package io.fabric8.maven.docker.access.hc;

import io.fabric8.maven.docker.access.chunked.EntityStreamReaderUtil;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

@SuppressWarnings("unused")
public class HcChunkedResponseHandlerWrapperTest {

    @Mocked
    private EntityStreamReaderUtil.JsonEntityResponseHandler handler;
    @Mocked
    private HttpResponse response;
    @Mocked
    private EntityStreamReaderUtil entityStreamReaderUtil;

    private Header[] headers;
    private HcChunkedResponseHandlerWrapper hcChunkedResponseHandlerWrapper;

    @Before
    public void setUp() {
        hcChunkedResponseHandlerWrapper = new HcChunkedResponseHandlerWrapper(handler);
    }

    @Test
    public void handleResponseWithJsonResponse() throws IOException {
        givenResponseHeaders(new BasicHeader("ConTenT-Type", "application/json; charset=UTF-8"));
        hcChunkedResponseHandlerWrapper.handleResponse(response);
        verifyProcessJsonStream(1);
    }

    @Test
    public void handleResponseWithTextPlainResponse() throws IOException {
        givenResponseHeaders(new BasicHeader("Content-Type", "text/plain"));
        hcChunkedResponseHandlerWrapper.handleResponse(response);
        verifyProcessJsonStream(0);
    }

    @Test
    public void handleResponseWithNoContentType() throws IOException {
        givenResponseHeaders();
        hcChunkedResponseHandlerWrapper.handleResponse(response);
        verifyProcessJsonStream(0);
    }

    private void givenResponseHeaders(Header... headers) {
        // @formatter:off
        new Expectations() {{
            response.getAllHeaders(); result = headers;
        }};
        // @formatter:on
    }

    @SuppressWarnings("AccessStaticViaInstance")
    private void verifyProcessJsonStream(int timesCalled) throws IOException {
        // @formatter:off
        new Verifications() {{
            entityStreamReaderUtil.processJsonStream(handler, response.getEntity().getContent()); times = timesCalled;
        }};
        // @formatter:on
    }
}
