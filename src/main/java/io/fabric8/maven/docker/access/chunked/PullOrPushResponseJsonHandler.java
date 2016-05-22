package io.fabric8.maven.docker.access.chunked;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.util.Logger;
import org.json.JSONObject;

public class PullOrPushResponseJsonHandler implements EntityStreamReaderUtil.JsonEntityResponseHandler {

    private final Logger log;
    
    public PullOrPushResponseJsonHandler(Logger log) {
        this.log = log;
    }
    
    @Override
    public void process(JSONObject json) throws DockerAccessException {
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

    private void logInfoMessage(JSONObject json) {
        String value;
        if (json.has("stream")) {
            value = json.getString("stream").replaceFirst("\n$", "");
        } else if (json.has("status")) {
            value = json.getString("status");
        } else {
            value = json.toString();
        }
        log.info("%s", value);
    }

    private void throwDockerAccessException(JSONObject json) throws DockerAccessException {
        String msg = json.getString("error").trim();
        String details = json.getJSONObject("errorDetail").getString("message").trim();
        throw new DockerAccessException("%s %s", msg, (msg.equals(details) ? "" : "(" + details + ")"));
    }

    private String getStringOrEmpty(JSONObject json, String what) {
        return json.has(what) ? json.getString(what) : "";
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
