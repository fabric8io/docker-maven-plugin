package io.fabric8.maven.docker.model;

import org.json.JSONArray;
import org.json.JSONObject;

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
        return json.getBoolean(RUNNING);
    }

    public Integer getExitCode() {
        if (isRunning()) {
            return null;
        }
        return json.getInt(EXIT_CODE);
    }

    public String getEntryPoint() {
        if (!json.has(PROCESS_CONFIG)) {
            return null;
        }
        JSONObject processConfig = json.getJSONObject(PROCESS_CONFIG);
        if (!processConfig.has(ENTRY_POINT)) {
            return null;
        }
        return processConfig.getString(ENTRY_POINT);
    }

    public String[] getArguments() {
        if (!json.has(PROCESS_CONFIG)) {
            return null;
        }
        JSONObject processConfig = json.getJSONObject(PROCESS_CONFIG);
        if (!processConfig.has(ARGUMENTS)) {
            return null;
        }
        JSONArray arguments = processConfig.getJSONArray(ARGUMENTS);
        String[] result = new String[arguments.length()];
        for (int i = 0; i < arguments.length(); i++) {
            result[i] = arguments.getString(i);
        }
        return result;
    }
}
