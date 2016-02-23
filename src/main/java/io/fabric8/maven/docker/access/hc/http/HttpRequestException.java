package io.fabric8.maven.docker.access.hc.http;

import java.io.IOException;

public class HttpRequestException extends IOException {

    public HttpRequestException(String message) {
        super(message);
    }
}
