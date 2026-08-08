package io.fabric8.maven.docker.access.hc;

import com.google.gson.JsonIOException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import io.fabric8.maven.docker.access.chunked.EntityStreamReaderUtil;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;

import java.io.*;
import java.util.stream.Stream;

public class HcChunkedResponseHandlerWrapper implements ResponseHandler<Object> {
    private final EntityStreamReaderUtil.JsonEntityResponseHandler handler;

    HcChunkedResponseHandlerWrapper(EntityStreamReaderUtil.JsonEntityResponseHandler handler) {
        this.handler = handler;
    }

    @Override
    public Object handleResponse(HttpResponse response) throws IOException {
        try (InputStream stream = response.getEntity().getContent()) {
            // A previous version of this file only detected JSON by the Content-Type header, which is not enough:
            // when the Podman daemon is used, the POST /build response is JSON with HTTP status 200 as expected, but
            // carries no "Content-Type: application/json" header - no Content-Type header at all. Seen in Podman 3.4.2.
            //
            // The plugin relies on that JSON body: BuildJsonResponseHandler.process() raises a DockerAccessException
            // for an "error" entry. Missing the body means no error is detected and the Maven build carries on even
            // though the image failed to build.
            //
            // Hence: if the header indicates application/json, stream the response to the handler. If there is no
            // Content-Type header at all, detect whether the body is JSON and, if so, call the handler with a
            // buffered response.
            if (isJsonCheckedByHeader(response)) {
                EntityStreamReaderUtil.processJsonStream(handler, stream);
            } else if (isMissingContentType(response)) {
                ByteArrayOutputStream baos = getMultipleReadbleOutputStream(stream);
                InputStream is = new ByteArrayInputStream(baos.toByteArray());
                if (isJsonCheckedByBody(is)) {
                    is = new ByteArrayInputStream(baos.toByteArray());
                    EntityStreamReaderUtil.processJsonStream(handler, is);
                }
            }
        }
        return null;
    }

    private ByteArrayOutputStream getMultipleReadbleOutputStream(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) > -1 ) {
            baos.write(buffer, 0, len);
        }
        baos.flush();
        return baos;
    }

    private static boolean isJsonCheckedByBody(InputStream is){
        try {
            JsonReader json = new JsonReader(new InputStreamReader(is));
            JsonParser parser = new JsonParser();
            parser.parse(json);
            // No exception until here: Content is JSON.
        } catch (JsonIOException | JsonSyntaxException e){
            // No JSON.
            return false;
        }
        return true;
    }

    private static boolean isMissingContentType(HttpResponse response){
        return Stream.of(response.getAllHeaders())
                .noneMatch(h -> h.getName().equalsIgnoreCase("Content-Type"));
    }

    private static boolean isJsonCheckedByHeader(HttpResponse response) {
        return Stream.of(response.getAllHeaders())
                .filter(h -> h.getName().equalsIgnoreCase("Content-Type"))
                .anyMatch(h -> h.getValue().toLowerCase().startsWith("application/json"));
    }
}
