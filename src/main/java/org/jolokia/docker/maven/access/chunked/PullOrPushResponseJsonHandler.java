package org.jolokia.docker.maven.access.chunked;

import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.util.Logger;
import org.json.JSONObject;

public class PullOrPushResponseJsonHandler implements EntityStreamReaderUtil.JsonEntityResponseHandler {

    private boolean downloadInProgress = false;

    private final Logger log;
    
    public PullOrPushResponseJsonHandler(Logger log) {
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
            throw new DockerAccessException("%s %s", msg, (msg.equals(details) ? "" : "(" + details + ")"));
        } else {
            if (json.length() > 0) {
                log.info("... " + extractInfo(json));
            }
        }
    }

    private String extractInfo(JSONObject json) {
        StringBuilder ret = new StringBuilder();
        for (String key : new String[] {"id", "status", "stream" }) {
            if (json.has(key)) {
                ret.append(json.getString(key));
                ret.append(" ");
            }
        }
        return ret.toString().trim();
    }
}
