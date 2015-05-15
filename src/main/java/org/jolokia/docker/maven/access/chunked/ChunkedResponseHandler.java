package org.jolokia.docker.maven.access.chunked;

import org.jolokia.docker.maven.access.DockerAccessException;

public interface ChunkedResponseHandler<T> {
    void process(T toProcess) throws DockerAccessException;
}
