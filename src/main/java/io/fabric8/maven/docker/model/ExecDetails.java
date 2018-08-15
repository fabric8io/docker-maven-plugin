package io.fabric8.maven.docker.model;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Model class holding details of the result of an exec command on a running container.
 */
public class ExecDetails {
    private static final String EXIT_CODE = "ExitCode";
    private static final String RUNNING = "Running";
    private static final String ENTRY_POINT = "entrypoint";
    private static final String ARGUMENTS = "arguments";

    private static final String PROCESS_CONFIG = "ProcessConfig";

    private final JSONObject json;

    public ExecDetails(JSONObject json) {
        this.json = json;
    }

    public boolean isRunning() {
        return json.optBoolean(RUNNING);
    }

    public Integer getExitCode() {
        if (isRunning()) {
            return null;
        }
        return json.optInt(EXIT_CODE);
    }

    public String getEntryPoint() {
        if (!json.has(PROCESS_CONFIG)) {
            return null;
        }
        JSONObject processConfig = json.optJSONObject(PROCESS_CONFIG);
        if (!processConfig.has(ENTRY_POINT)) {
            return null;
        }
        return processConfig.optString(ENTRY_POINT);
    }

    public String[] getArguments() {
        if (!json.has(PROCESS_CONFIG)) {
            return null;
        }
        JSONObject processConfig = json.optJSONObject(PROCESS_CONFIG);
        if (!processConfig.has(ARGUMENTS)) {
            return null;
        }
        JSONArray arguments = processConfig.optJSONArray(ARGUMENTS);
        String[] result = new String[arguments.length()];
        for (int i = 0; i < arguments.length(); i++) {
            result[i] = arguments.optString(i);
        }
        return result;
    }
}
