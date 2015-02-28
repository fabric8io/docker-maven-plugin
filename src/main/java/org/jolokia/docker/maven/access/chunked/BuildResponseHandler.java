package org.jolokia.docker.maven.access.chunked;

import org.jolokia.docker.maven.util.Logger;
import org.json.JSONObject;

public class BuildResponseHandler implements ChunkedResponseHandler<JSONObject> {

    private final Logger log;

    public BuildResponseHandler(Logger log) {
        this.log = log;
    }
    
    @Override
    public void process(JSONObject json) {
        if (json.has("error")) {
            log.error("Error building image: " + json.get("error"));
            if (json.has("errorDetail")) {
                JSONObject details = json.getJSONObject("errorDetail");
                log.error(details.getString("message"));
            }
        } else if (json.has("stream")) {
            String message = json.getString("stream");
            log.debug(message.trim());
        } else if (json.has("status")) {
            String status = json.getString("status").trim();
            String id = json.has("id") ? json.getString("id") : null;
            if (status.matches("^.*(Download|Pulling).*")) {
                log.info("  " + (id != null ? id + " " : "") + status);
            }
        }
    }
}
