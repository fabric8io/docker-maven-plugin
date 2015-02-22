package org.jolokia.docker.maven.access.chunked;

import org.jolokia.docker.maven.util.AnsiLogger;
import org.json.JSONException;
import org.json.JSONObject;


public class TextToJsonBridgeCallback implements ChunkedResponseHandler<String>
{
    private final ChunkedResponseHandler<JSONObject> handler;
    private final AnsiLogger log;
    
    public TextToJsonBridgeCallback(AnsiLogger log, ChunkedResponseHandler<JSONObject> handler) {
        this.log = log;
        this.handler = handler;
    }

    @Override
    public void process(String text) {
        try {
            JSONObject json = new JSONObject(text);
            handler.process(json);
        } catch (JSONException exp) {
            log.warn("Couldn't parse answer chunk '" + text + "': " + exp);
        }
    }
}