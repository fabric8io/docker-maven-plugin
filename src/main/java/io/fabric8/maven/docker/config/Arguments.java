package io.fabric8.maven.docker.config;

import io.fabric8.maven.docker.util.EnvUtil;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.Serializable;
import java.util.*;

public class Arguments implements Serializable {

    @Parameter
    private String shell;

    @Parameter
    private List<String> exec;

    /**
     * Used to distinguish between shorter version
     *
     * <pre>
     *   &lt;cmd&gt;
     *     &lt;arg&gt;echo&lt;/arg&gt;
     *     &lt;arg&gt;Hello, world!&lt;/arg&gt;
     *   &lt;/cmd&gt;
     * </pre>
     *
     * from the full one
     *
     * <pre>
     *   &lt;cmd&gt;
     *     &lt;exec&gt;
     *       &lt;arg&gt;echo&lt;/arg&gt;
     *       &lt;arg&gt;Hello, world!&lt;/arg&gt;
     *     &lt;exec&gt;
     *   &lt;/cmd&gt;
     * </pre>
     *
     * and throw a validation error if both specified.
     */
    private List<String> execInlined = new ArrayList<>();

    public Arguments() { }

    public Arguments(String shell) {
        this.shell = shell;
    }

    public Arguments(List<String> exec) { this.exec = exec; }

    /**
     * Used to support shell specified as a default parameter, e.g.
     *
     * <pre>
     *   &lt;cmd&gt;java -jar $HOME/server.jar&lt;/cmd&gt;
     * </pre>
     *
     * Read <a href="http://blog.sonatype.com/2011/03/configuring-plugin-goals-in-maven-3/#.VeR3JbQ56Rv">more</a> on
     * this and other useful techniques.
     *
     */
    public void set(String shell) {
        setShell(shell);
    }

    public void setShell(String shell) {
        this.shell = shell;
    }

    public String getShell() {
        return shell;
    }

    public void setExec(List<String> exec) {
        this.exec = exec;
    }

    /**
     * @see Arguments#execInlined
     */
    @SuppressWarnings("unused")
    public void setArg(String arg) {
        this.execInlined.add(arg);
    }

    public List<String> getExec() {
        return exec == null ? execInlined : exec;
    }

    public void validate() throws IllegalArgumentException {
        int valueSources = 0;
        if (shell != null) {
            valueSources ++;
        }
        if (exec != null && !exec.isEmpty()) {
            valueSources ++;
        }
        if (!execInlined.isEmpty()) {
            valueSources ++;
        }

        if (valueSources != 1){
            throw new IllegalArgumentException("Argument conflict: either shell or args should be specified and only in one form.");
        }
    }

    public List<String> asStrings() {
        if (shell != null) {
            return Arrays.asList(EnvUtil.splitOnSpaceWithEscape(shell));
        }
        if (exec != null) {
            return Collections.unmodifiableList(exec);
        }
        return Collections.unmodifiableList(execInlined);
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
