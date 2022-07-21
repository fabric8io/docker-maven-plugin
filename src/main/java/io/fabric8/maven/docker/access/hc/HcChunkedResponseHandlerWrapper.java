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
            ByteArrayOutputStream baos = getMultipleReadbleOutputStream(stream);
            InputStream is = new ByteArrayInputStream(baos.toByteArray());
            // In the previous version of this file the following if() was as follows. The methode isJson() checks
            // by header (not by body):
            //      if(isJson(response)) {
            //          EntityStreamReaderUtil.processJsonStream(handler, stream);
            //      }
            //
            // In case the Podman daemon is used, the POST /build response is JSON and the HTTP status code is 200
            // as expected, but there is no header "Content-Type = application/json" nor "Content-Type" at all. Seen in
            // Podman 3.4.2. But the docker-maven-plugin relies on that JSON-body. In BuildJsonResponseHandler.process():
            //      if (json.has("error")) {
            //          ...
            //          throw new DockerAccessException();
            //      ...
            //
            // If no error is detected, the Maven-build goes on despite there was a problem building the
            // image!
            // The following if() first checks for the application/json Content-Type. If no Content-Type is set,
            // it tries to detect if the body is JSON. If so, the handler is called.
            if(isJsonCheckedByHeader(response) || (isMissingContentType(response) && isJsonCheckedByBody(is))){
                is = new ByteArrayInputStream(baos.toByteArray());
                EntityStreamReaderUtil.processJsonStream(handler, is);
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
