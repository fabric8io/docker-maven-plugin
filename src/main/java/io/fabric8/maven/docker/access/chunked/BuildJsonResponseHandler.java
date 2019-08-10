package io.fabric8.maven.docker.access.chunked;

import com.google.gson.JsonObject;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.util.Logger;

public class BuildJsonResponseHandler implements EntityStreamReaderUtil.JsonEntityResponseHandler {

    private final Logger log;

    public BuildJsonResponseHandler(Logger log) {
        this.log = log;
    }
    
    @Override
    public void process(JsonObject json) throws DockerAccessException {
        if (json.has("error")) {
            String msg = json.get("error").getAsString();
            String detailMsg = "";
            if (json.has("errorDetail")) {
                JsonObject details = json.getAsJsonObject("errorDetail");
                detailMsg = details.get("message").getAsString();
            }
            throw new DockerAccessException("%s %s", json.get("error"),
                    (msg.equals(detailMsg) || "".equals(detailMsg) ? "" : "(" + detailMsg + ")"));
        } else if (json.has("stream")) {
            String message = json.get("stream").getAsString();
            log.verbose(Logger.LogVerboseCategory.BUILD,"%s", message.trim());
        } else if (json.has("status")) {
            String status = json.get("status").getAsString().trim();
            String id = json.has("id") ? json.get("id").getAsString() : null;
            if (status.matches("^.*(Download|Pulling).*")) {
                log.info("  %s%s",id != null ? id + " " : "",status);
            }
        }
    }

    // Lifecycle methods not needed ...
    @Override
    public void start() {}

    @Override
    public void stop() {}
}
