package io.fabric8.maven.docker.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

public class GsonBridge {
    private static final Gson GSON = new Gson();

    private GsonBridge() {}

    public static final JsonObject toJsonObject(String json) {
        return GSON.fromJson(json, JsonObject.class);
    }

    public static final JsonArray toJsonArray(String json) {
        return GSON.fromJson(json, JsonArray.class);
    }

    public static final JsonArray toJsonArray(List<String> list) {
        final JsonArray jsonArray = new JsonArray();

        for(String element : list)
        {
            jsonArray.add(element);
        }

        return jsonArray;
    }

    public static final JsonObject toJsonObject(Map<String,String> map) {
        final JsonObject jsonObject = new JsonObject();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            jsonObject.addProperty(entry.getKey(), entry.getValue());
        }

        return jsonObject;
    }
}
