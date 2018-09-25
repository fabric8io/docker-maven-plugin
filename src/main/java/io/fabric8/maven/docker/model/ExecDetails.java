package io.fabric8.maven.docker.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Model class holding details of the result of an exec command on a running container.
 */
public class ExecDetails {
    private static final String EXIT_CODE = "ExitCode";
    private static final String RUNNING = "Running";
    private static final String ENTRY_POINT = "entrypoint";
    private static final String ARGUMENTS = "arguments";

    private static final String PROCESS_CONFIG = "ProcessConfig";

    private final JsonObject json;

    public ExecDetails(JsonObject json) {
        this.json = json;
    }

    public boolean isRunning() {
        return json.get(RUNNING).getAsBoolean();
    }

    public Integer getExitCode() {
        if (isRunning()) {
            return null;
        }
        return json.get(EXIT_CODE).getAsInt();
    }

    public String getEntryPoint() {
        if (!json.has(PROCESS_CONFIG)) {
            return null;
        }

        JsonObject processConfig = json.getAsJsonObject(PROCESS_CONFIG);
        if (!processConfig.has(ENTRY_POINT)) {
            return null;
        }

        return processConfig.get(ENTRY_POINT).getAsString();
    }

    public String[] getArguments() {
        if (!json.has(PROCESS_CONFIG)) {
            return null;
        }
        JsonObject processConfig = json.getAsJsonObject(PROCESS_CONFIG);
        if (!processConfig.has(ARGUMENTS)) {
            return null;
        }
        JsonArray arguments = processConfig.getAsJsonArray(ARGUMENTS);
        String[] result = new String[arguments.size()];
        for (int i = 0; i < arguments.size(); i++) {
            result[i] = arguments.get(i).getAsString();
        }
        return result;
    }
}
