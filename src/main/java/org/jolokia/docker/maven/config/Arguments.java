package org.jolokia.docker.maven.config;

import java.util.ArrayList;
import java.util.List;

public class Arguments {

    /**
     * @parameter
     */
    private String shell;

    /**
     * @parameter
     */
    private List<String> exec;

    public void setShell(String shell) {
        this.shell = shell;
    }

    public String getShell() {
        return shell;
    }

    public void setExec(List<String> exec) {
        this.exec = exec;
    }

    public List<String> getExec() {
        return exec;
    }

    public void validate() throws IllegalArgumentException {
        if (shell == null && (exec == null || exec.isEmpty())){
            throw new IllegalArgumentException("Argument conflict, either shell or params should be specified");
        }
        if (shell != null && exec != null) {
            throw new IllegalArgumentException("Argument conflict, either shell or params should be specified");
        }
    }

    public static class Builder {
        private String shell;
        private List<String> params;

        public static Builder get(){
            return new Builder();
        }

        public Builder withShell(String shell){
            this.shell = shell;
            return this;
        }

        public Builder withParam(String param){
            if (params == null) {
                params = new ArrayList<>();
            }
            this.params.add(param);
            return this;
        }

        public Arguments build(){
            Arguments a = new Arguments();
            a.setShell(shell);
            a.setExec(params);
            return a;
        }
    }
}
