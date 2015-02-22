package org.jolokia.docker.maven.access.chunked;

public interface ChunkedResponseHandler<T> {
    void process(T toProcess);
}
