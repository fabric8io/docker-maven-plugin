package io.fabric8.maven.docker.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import io.fabric8.maven.docker.model.JsonParsingException;

public class JsonUtils {
    private static final String ERROR_PUT = "Error putting key: %sttvalue: %s";
    private static final String ERROR_GET = "Error getting key: %s";
    private static final String ERROR_JOINING = "Error joining: %s";
    private static final String ERROR_CREATING = "Error creating %s";

    private JsonUtils() {}

    public static final void putNonNull(JSONObject ret, String key, String value) {
        if (value != null) {
            try {
                ret.put(key,value);
            } catch (JSONException e) {
                throw new JsonParsingException(String.format(ERROR_PUT, key, value), e );
            }
        }
    }

    public static final void put(JSONObject ret, String key, Object value) {
        try {
            ret.put(key,value);
        } catch (JSONException e) {
            throw new JsonParsingException(String.format(ERROR_PUT, key, value), e );
        }
    }

    public static final Object get(JSONObject ret, String key) {
        try {
            return ret.get(key);
        } catch (JSONException e) {
            throw new JsonParsingException(String.format(ERROR_GET, key), e );
        }
    }

    public static final String join(JSONArray joinMe, String separator) {
        try {
            return joinMe.join(separator);
        } catch (JSONException e) {
            throw new JsonParsingException(String.format(ERROR_JOINING, joinMe), e );
        }
    }

    public static final JSONObject toJSONObject(String json) {
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            throw new JsonParsingException(String.format(ERROR_CREATING, json), e );
        }
    }

    public static final JSONObject toJSONObject(JSONTokener json) {
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            throw new JsonParsingException(String.format(ERROR_CREATING, json), e );
        }
    }

    public static final JSONArray toJSONArray(String json) {
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            throw new JsonParsingException(String.format(ERROR_CREATING, json), e );
        }
    }
}
