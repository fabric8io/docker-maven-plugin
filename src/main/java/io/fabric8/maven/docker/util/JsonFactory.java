package io.fabric8.maven.docker.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

public class JsonFactory {
    private static final Gson GSON = new Gson();

    private JsonFactory() {
        // Empty Constructor
    }

    public static JsonObject newJsonObject(String json) {
        return GSON.fromJson(json, JsonObject.class);
    }

    public static JsonArray newJsonArray(String json) {
        return GSON.fromJson(json, JsonArray.class);
    }

    public static JsonArray newJsonArray(List<String> list) {
        final JsonArray jsonArray = new JsonArray();

        for(String element : list)
        {
            jsonArray.add(element);
        }

        return jsonArray;
    }

    public static JsonObject newJsonObject(Map<String,String> map) {
        final JsonObject jsonObject = new JsonObject();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            jsonObject.addProperty(entry.getKey(), entry.getValue());
        }

        return jsonObject;
    }
}
