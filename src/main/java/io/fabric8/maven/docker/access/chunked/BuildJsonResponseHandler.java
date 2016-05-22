package io.fabric8.maven.docker.access.chunked;

import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.access.DockerAccessException;
import org.json.JSONObject;

public class BuildJsonResponseHandler implements EntityStreamReaderUtil.JsonEntityResponseHandler {

    private final Logger log;

    public BuildJsonResponseHandler(Logger log) {
        this.log = log;
    }
    
    @Override
    public void process(JSONObject json) throws DockerAccessException {
        if (json.has("error")) {
            String msg = json.getString("error");
            String detailMsg = "";
            if (json.has("errorDetail")) {
                JSONObject details = json.getJSONObject("errorDetail");
                detailMsg = details.getString("message");
            }
            throw new DockerAccessException("%s %s", json.get("error"),
                    (msg.equals(detailMsg) || "".equals(detailMsg) ? "" : "(" + detailMsg + ")"));
        } else if (json.has("stream")) {
            String message = json.getString("stream");
            log.verbose("%s", message.trim());
        } else if (json.has("status")) {
            String status = json.getString("status").trim();
            String id = json.has("id") ? json.getString("id") : null;
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
