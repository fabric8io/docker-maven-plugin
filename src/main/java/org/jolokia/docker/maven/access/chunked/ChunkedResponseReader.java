package org.jolokia.docker.maven.access.chunked;

import org.jolokia.docker.maven.access.DockerAccessException;

import java.io.IOException;
import java.io.InputStream;

public class ChunkedResponseReader {

    private final InputStream stream;
    private final ChunkedResponseHandler<String> handler;
    
    public ChunkedResponseReader(InputStream stream, ChunkedResponseHandler<String> handler) {
        this.stream = stream;
        this.handler = handler;
    }        
    
    public void process() throws IOException, DockerAccessException {
        int len;
        int size = 8129;
        byte[] buf = new byte[size];
        // Data comes in chunkwise
        while ((len = stream.read(buf, 0, size)) != -1) {
            String txt = new String(buf, 0, len, "UTF-8");
            handler.process(txt);
        }
    }
}
