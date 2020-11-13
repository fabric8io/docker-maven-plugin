package io.fabric8.maven.docker.access.hc;

import io.fabric8.maven.docker.access.chunked.EntityStreamReaderUtil;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

public class HcChunkedResponseHandlerWrapper implements ResponseHandler<Object> {
    private final EntityStreamReaderUtil.JsonEntityResponseHandler handler;

    HcChunkedResponseHandlerWrapper(EntityStreamReaderUtil.JsonEntityResponseHandler handler) {
        this.handler = handler;
    }

    @Override
    public Object handleResponse(HttpResponse response) throws IOException {
        try (InputStream stream = response.getEntity().getContent()) {
            // Parse text as json
            if (isJson(response)) {
                EntityStreamReaderUtil.processJsonStream(handler, stream);
            }
        }
        return null;
    }

    private static boolean isJson(HttpResponse response) {
        return Stream.of(response.getAllHeaders())
                .filter(h -> h.getName().equalsIgnoreCase("Content-Type"))
                .anyMatch(h -> h.getValue().toLowerCase().startsWith("application/json"));
    }
}
