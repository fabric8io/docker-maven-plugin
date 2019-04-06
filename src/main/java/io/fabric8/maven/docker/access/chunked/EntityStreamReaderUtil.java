package io.fabric8.maven.docker.access.chunked;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import io.fabric8.maven.docker.access.DockerAccessException;

public class EntityStreamReaderUtil {

    private EntityStreamReaderUtil() {}

    public static void processJsonStream(JsonEntityResponseHandler handler, InputStream stream) throws IOException {
        handler.start();
        try(JsonReader json = new JsonReader(new InputStreamReader(stream))) {
            JsonParser parser = new JsonParser();

            json.setLenient(true);
            while (json.peek() != JsonToken.END_DOCUMENT) {
                JsonElement element = parser.parse(json);
                handler.process(element.getAsJsonObject());
            }
        } finally {
            handler.stop();
        }
    }

    public interface JsonEntityResponseHandler {
        void process(JsonObject toProcess) throws DockerAccessException;
        void start();
        void stop();
    }
}
