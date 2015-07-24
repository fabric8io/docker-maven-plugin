package org.jolokia.docker.maven.access.hc.http;

import java.io.IOException;

public class HttpRequestException extends IOException {

    public HttpRequestException(String message) {
        super(message);
    }
}
