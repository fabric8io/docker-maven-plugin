package org.jolokia.docker.maven.access.chunked;

import org.jolokia.docker.maven.util.AnsiLogger;
import org.json.JSONObject;

public class PullOrPushResponseHandler implements ChunkedResponseHandler<JSONObject> {

    private boolean downloadInProgress = false;

    private final AnsiLogger log;
    
    public PullOrPushResponseHandler(AnsiLogger log) {
        this.log = log;
    }
    
    @Override
    public void process(JSONObject json) {
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
        } else {
            log.info("... " + (json.has("id") ? json.getString("id") + ": " : "") + json.getString("status"));
        }
    }
}
