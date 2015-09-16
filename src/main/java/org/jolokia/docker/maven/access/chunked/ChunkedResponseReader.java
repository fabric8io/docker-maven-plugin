package org.jolokia.docker.maven.access.chunked;

import java.io.IOException;
import java.io.InputStream;

import org.jolokia.docker.maven.access.DockerAccessException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class ChunkedResponseReader {

    private final InputStream stream;
    private final ChunkedResponseHandler handler;
    private final JSONTokener tokener;

    public ChunkedResponseReader(InputStream stream, ChunkedResponseHandler handler) {
        this.stream = stream;
        this.handler = handler;
        this.tokener = new JSONTokener(stream);
    }        
    
    public void process() throws IOException {
        while (true) {
            char next = tokener.nextClean();
            if (next == 0) {
                return;
            } else {
                tokener.back();
            }
            JSONObject object = new JSONObject(tokener);
            handler.process(object);
        }
    }

    public interface ChunkedResponseHandler {
        void process(JSONObject toProcess) throws DockerAccessException;
    }
}
