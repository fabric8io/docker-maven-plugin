package io.fabric8.maven.docker.access.chunked;

import org.json.JSONObject;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.util.Logger;

import static io.fabric8.maven.docker.util.JsonUtils.get;

public class BuildJsonResponseHandler implements EntityStreamReaderUtil.JsonEntityResponseHandler {

    private final Logger log;

    public BuildJsonResponseHandler(Logger log) {
        this.log = log;
    }
    
    @Override
    public void process(JSONObject json) throws DockerAccessException {
        if (json.has("error")) {
            String msg = json.optString("error");
            String detailMsg = "";
            if (json.has("errorDetail")) {
                JSONObject details = json.optJSONObject("errorDetail");
                detailMsg = details.optString("message");
            }
            throw new DockerAccessException("%s %s", get(json, "error"),
                    (msg.equals(detailMsg) || "".equals(detailMsg) ? "" : "(" + detailMsg + ")"));
        } else if (json.has("stream")) {
            String message = json.optString("stream");
            log.verbose("%s", message.trim());
        } else if (json.has("status")) {
            String status = json.optString("status").trim();
            String id = json.has("id") ? json.optString("id") : null;
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
