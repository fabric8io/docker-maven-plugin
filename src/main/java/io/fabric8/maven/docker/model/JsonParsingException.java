package io.fabric8.maven.docker.model;

public class JsonParsingException extends RuntimeException {
    public JsonParsingException(Throwable cause) {
        super(cause);
    }

    public JsonParsingException(String message, Throwable cause) {
        super(message, cause);
    }

    public JsonParsingException(String message) {
        super(message);
    }

    public JsonParsingException() {
        super();
    }
}
