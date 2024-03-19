package io.fabric8.maven.docker.config;

import io.fabric8.maven.docker.assembly.DockerFileKeyword;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class RunCommand implements Serializable {
    protected DockerFileKeyword action;
    protected String params;

    public RunCommand() {
    }

    public RunCommand(String cmd) {
        this(DockerFileKeyword.RUN, cmd);
    }

    public RunCommand(DockerFileKeyword action, String param) {
        this.action = action;
        this.params = param;
    }

    public static List<RunCommand> run(String... commands) {
        return Arrays.stream(commands).map(RunCommand::new).collect(Collectors.toList());
    }

    public DockerFileKeyword getAction() {
        return action;
    }

    public String getParams() {
        return params;
    }

    public void set(RunCommand rc) {
        if(rc == null) return;
        action = rc.action;
        params = rc.params;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RunCommand that = (RunCommand) o;
        return action == that.action && Objects.equals(params, that.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, params);
    }

    @Override
    public String toString() {
        return "RunCommand{" +
                "action=" + action +
                ", params='" + params + '\'' +
                '}';
    }
}
