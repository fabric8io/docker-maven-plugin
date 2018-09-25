package io.fabric8.maven.docker.access.chunked;

import com.google.gson.JsonObject;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.util.Logger;

public class PullOrPushResponseJsonHandler implements EntityStreamReaderUtil.JsonEntityResponseHandler {

    private final Logger log;
    
    public PullOrPushResponseJsonHandler(Logger log) {
        this.log = log;
    }
    
    @Override
    public void process(JsonObject json) throws DockerAccessException {
        if (json.has("progressDetail")) {
            log.progressUpdate(getStringOrEmpty(json, "id"),
                               getStringOrEmpty(json, "status"),
                               getStringOrEmpty(json, "progress"));
        } else if (json.has("error")) {
            throwDockerAccessException(json);
        } else {
            log.progressFinished();
            logInfoMessage(json);
            log.progressStart();
        }
    }

    private void logInfoMessage(JsonObject json) {
        String value;
        if (json.has("stream")) {
            value = json.get("stream").getAsString().replaceFirst("\n$", "");
        } else if (json.has("status")) {
            value = json.get("status").getAsString();
        } else {
            value = json.toString();
        }
        log.info("%s", value);
    }

    private void throwDockerAccessException(JsonObject json) throws DockerAccessException {
        String msg = json.get("error").getAsString().trim();
        String details = json.getAsJsonObject("errorDetail").get("message").getAsString().trim();
        throw new DockerAccessException("%s %s", msg, (msg.equals(details) ? "" : "(" + details + ")"));
    }

    private String getStringOrEmpty(JsonObject json, String what) {
        return json.has(what) ? json.get(what).getAsString() : "";
    }

    @Override
    public void start() {
        log.progressStart();
    }

    @Override
    public void stop() {
        log.progressFinished();
    }
}
