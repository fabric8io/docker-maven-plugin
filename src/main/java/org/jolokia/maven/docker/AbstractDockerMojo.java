package org.jolokia.maven.docker;

import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.Parameter;
import org.fusesource.jansi.AnsiConsole;

/**
 * @author roland
 * @since 26.03.14
 */
abstract public class AbstractDockerMojo extends AbstractMojo {

    protected static final String PROPERTY_CONTAINER_ID = "docker.containerId";

    @Parameter(property = "docker.url",defaultValue = "http://localhost:4243")
    private String url;

    @Parameter(property = "docker.useColor", defaultValue = "false")
    private boolean color;

    private String errorHlColor,infoHlColor,warnHlColor,resetColor;

    protected DockerAccess createDockerAccess() {
        return new DockerAccess(url.replace("^tcp://","http://"),getLog());
    }


    public void execute() throws MojoExecutionException, MojoFailureException {
        init();
        try {
            doExecute();
        } catch (MojoExecutionException exp) {
            throw new MojoExecutionException(errorHlColor + exp.getMessage() + resetColor,exp);
        }
    }

    protected abstract void doExecute() throws MojoExecutionException, MojoFailureException;

    protected void init() {
        initColorLog();
        if (color) {

            errorHlColor = "\u001B[0;31m";
            infoHlColor = "\u001B[0;32m";
            resetColor = "\u001B[0;39m";
            warnHlColor = "\u001B[0;33m";
        } else {
            errorHlColor = infoHlColor = resetColor = warnHlColor = "";
        }
    }
    protected void info(String info) {
        getLog().info(infoHlColor + info + resetColor);
    }

    protected void warn(String warn) {
        getLog().warn(warnHlColor + warn + resetColor);
    }
    private void initColorLog() {
        if (color) {
            AnsiConsole.systemInstall();
        }
    }

    protected void error(String error) {
        getLog().error(errorHlColor + error + resetColor);
    }

    protected boolean isColor() {
        return color;
    }
}
