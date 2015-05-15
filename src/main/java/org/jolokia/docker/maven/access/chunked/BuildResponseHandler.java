package org.jolokia.docker.maven.access.chunked;

import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.util.Logger;
import org.json.JSONObject;

public class BuildResponseHandler implements ChunkedResponseHandler<JSONObject> {

    private final Logger log;

    public BuildResponseHandler(Logger log) {
        this.log = log;
    }
    
    @Override
    public void process(JSONObject json) throws DockerAccessException {
        if (json.has("error")) {
            String msg = json.getString("error");
            log.error("Error building image: " + msg);

            String detailMsg = "";
            if (json.has("errorDetail")) {
                JSONObject details = json.getJSONObject("errorDetail");
                detailMsg = details.getString("message");
                log.error(detailMsg);
            }
            throw new DockerAccessException("%s %s", json.get("error"),
                    (msg.equals(detailMsg) || "".equals(detailMsg) ? "" : "(" + detailMsg + ")"));
        } else if (json.has("stream")) {
            String message = json.getString("stream");
            log.verbose(message.trim());
        } else if (json.has("status")) {
            String status = json.getString("status").trim();
            String id = json.has("id") ? json.getString("id") : null;
            if (status.matches("^.*(Download|Pulling).*")) {
                log.info("  " + (id != null ? id + " " : "") + status);
            }
        }
    }
}
