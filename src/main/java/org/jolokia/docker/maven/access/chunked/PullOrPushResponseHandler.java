package org.jolokia.docker.maven.access.chunked;

import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.util.Logger;
import org.json.JSONObject;

public class PullOrPushResponseHandler implements ChunkedResponseReader.ChunkedResponseHandler {

    private boolean downloadInProgress = false;

    private final Logger log;
    
    public PullOrPushResponseHandler(Logger log) {
        this.log = log;
    }
    
    @Override
    public void process(JSONObject json) throws DockerAccessException {
        if (json.has("progressDetail")) {
            JSONObject details = json.getJSONObject("progressDetail");
            if (details.has("total")) {
                if (!downloadInProgress) {
                    log.progressStart(details.getInt("total"));
                }
                log.progressUpdate(details.getInt("current"));
                downloadInProgress = true;
                return;
            } 
               
            if (downloadInProgress) {
                log.progressFinished();
            }
            downloadInProgress = false;
        }
        if (json.has("error")) {
            String msg = json.getString("error").trim();
            String details = json.getJSONObject("errorDetail").getString("message").trim();
            log.error(msg + (msg.equals(details) ? "" : "(" + details + ")"));
            throw new DockerAccessException("%s %s", msg, (msg.equals(details) ? "" : "(" + details + ")"));
        } else {
            if (json.length() > 0) {
                log.info("... " + extractInfo(json));
            }
        }
    }

    private String extractInfo(JSONObject json) {
        if (json.has("id")) {
            StringBuffer ret = new StringBuffer();
            ret.append(json.getString("id"));
            if (json.has("status")) {
                ret.append(" ").append(json.getString("status"));
            }
            return ret.toString();
        } else if (json.has("stream")) {
            return json.getString("stream");
        } else {
            return "";
        }
    }
}
