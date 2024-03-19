package io.fabric8.maven.docker.config;

import io.fabric8.maven.docker.assembly.DockerFileKeyword;

import java.io.Serializable;
import java.util.Objects;

public class RunCommandParam implements Serializable {
    protected DockerFileKeyword action;
    protected String params;

    public RunCommandParam(DockerFileKeyword action, String param) {
        this.action = action;
        this.params = param;
    }

    public DockerFileKeyword getAction() {
        return action;
    }

    public String getParams() {
        return params;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RunCommandParam that = (RunCommandParam) o;
        return action == that.action && Objects.equals(params, that.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, params);
    }

    @Override
    public String toString() {
        return "RunCommandParam{" +
                "action=" + action +
                ", params='" + params + '\'' +
                '}';
    }
}
