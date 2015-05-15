package org.jolokia.docker.maven.access.chunked;

import org.jolokia.docker.maven.access.DockerAccessException;
import org.jolokia.docker.maven.util.Logger;
import org.json.JSONException;
import org.json.JSONObject;


public class TextToJsonBridgeCallback implements ChunkedResponseHandler<String>
{
    private final ChunkedResponseHandler<JSONObject> handler;
    private final Logger log;
    
    public TextToJsonBridgeCallback(Logger log, ChunkedResponseHandler<JSONObject> handler) {
        this.log = log;
        this.handler = handler;
    }

    @Override
    public void process(String text) throws DockerAccessException {
        try {
            JSONObject json = new JSONObject(text);
            handler.process(json);
        } catch (JSONException exp) {
            log.warn("Couldn't parse answer chunk '" + text + "': " + exp);
        }
    }
}