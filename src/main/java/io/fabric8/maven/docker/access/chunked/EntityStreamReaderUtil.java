package io.fabric8.maven.docker.access.chunked;

import java.io.IOException;
import java.io.InputStream;

import io.fabric8.maven.docker.access.DockerAccessException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class EntityStreamReaderUtil {

    private EntityStreamReaderUtil() {}

    public static void processJsonStream(JsonEntityResponseHandler handler, InputStream stream) throws IOException {
        handler.start();
        try {
            JSONTokener tokener = new JSONTokener(stream);
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
        } finally {
            handler.stop();
        }
    }

    public interface JsonEntityResponseHandler {
        void process(JSONObject toProcess) throws DockerAccessException;
        void start();
        void stop();
    }
}
