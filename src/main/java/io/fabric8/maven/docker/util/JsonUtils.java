package io.fabric8.maven.docker.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import io.fabric8.maven.docker.model.JsonParsingException;

public class JsonUtils {
    public static final void putNonNull(JSONObject ret, String key, String value) {
        if (value != null) {
            try {
                ret.put(key,value);
            } catch (JSONException e) {
                throw new JsonParsingException("Error putting key: " + key + "\tvalue: " + value, e );
            }
        }
    }

    public static final void put(JSONObject ret, String key, Object value) {
        try {
            ret.put(key,value);
        } catch (JSONException e) {
            throw new JsonParsingException("Error putting key: " + key + "\tvalue: " + value, e );
        }
    }

    public static final Object get(JSONObject ret, String key) {
        try {
            return ret.get(key);
        } catch (JSONException e) {
            throw new JsonParsingException("Error getting key: " + key, e );
        }
    }

    public static final String join(JSONArray joinMe, String separator) {
        try {
            return joinMe.join(separator);
        } catch (JSONException e) {
            throw new JsonParsingException("Error joining: " + joinMe, e );
        }
    }

    public static final JSONObject toJSONObject(String json) {
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            throw new JsonParsingException("Error creating: " + json, e );
        }
    }

    public static final JSONObject toJSONObject(JSONTokener json) {
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            throw new JsonParsingException("Error creating: " + json, e );
        }
    }

    public static final JSONArray toJSONArray(String json) {
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            throw new JsonParsingException("Error creating: " + json, e );
        }
    }
}
