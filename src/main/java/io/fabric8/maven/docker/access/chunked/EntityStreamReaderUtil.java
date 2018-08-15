package io.fabric8.maven.docker.access.chunked;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import io.fabric8.maven.docker.access.DockerAccessException;

import static io.fabric8.maven.docker.util.JsonUtils.toJSONObject;

public class EntityStreamReaderUtil {

    private EntityStreamReaderUtil() {}

    public static void processJsonStream(JsonEntityResponseHandler handler, InputStream stream) throws IOException, JSONException {
        handler.start();
        try {
            JSONTokener tokener = new JSONTokener(IOUtils.toString(stream, Charset.defaultCharset()));
            while (true) {
                char next = tokener.nextClean();
                if (next == 0) {
                    return;
                } else {
                    tokener.back();
                }
                JSONObject object = toJSONObject(tokener);
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
