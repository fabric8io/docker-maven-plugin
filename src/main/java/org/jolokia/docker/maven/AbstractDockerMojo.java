package org.jolokia.docker.maven;

import java.util.*;

import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.fusesource.jansi.AnsiConsole;

/**
 * Base class for this plugin.
 *
 * @author roland
 * @since 26.03.14
 */
abstract public class AbstractDockerMojo extends AbstractMojo implements LogHandler {

    // prefix used for console output
    private static final String LOG_PREFIX = "DOCKER> ";

    // Current maven project
    @Component
    protected MavenProject project;

    // URL to docker daemon
    @Parameter(property = "docker.url",defaultValue = "http://localhost:2375")
    private String url;

    // Whether to use color
    @Parameter(property = "docker.useColor", defaultValue = "true")
    private boolean color;

    // ANSI escapes for various colors (or empty strings if no coloring is used)
    private String errorHlColor,infoHlColor,warnHlColor,resetColor,progressHlColor;

    /**
     * Entry point for this plugin. It will set up the helper class and then calls {@link #executeInternal(DockerAccess)}
     * which must be implemented by subclass.
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        colorInit();
        DockerAccess access = new DockerAccessUnirest(url.replace("^tcp://", "http://"), this);
        access.start();
        try {
            executeInternal(access);
        } catch (MojoExecutionException exp) {
            throw new MojoExecutionException(errorHlColor + exp.getMessage() + resetColor,exp);
        } finally {
            access.shutdown();
        }

    }

    /**
     * Hook for subclass for doing the real job
     *
     * @param dockerAccess access object for getting to the DockerServer
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    protected abstract void executeInternal(DockerAccess dockerAccess)
            throws MojoExecutionException, MojoFailureException;

    // =============================================================================================
    // Registry for managed containers

    // Set with all registered shutdown actions
    private static Set<ShutdownAction> shutdownActions =
            Collections.synchronizedSet(new LinkedHashSet<ShutdownAction>());

    /**
     * Register a shutdown action executed during "stop"
     * @param shutdownAction action to register
     */
    protected static void registerShutdownAction(ShutdownAction shutdownAction) {
        shutdownActions.add(shutdownAction);
    }

    /**
     * Return shutdown actions in reverse registration order
     * @return registered shutdown actions
     */
    protected static List<ShutdownAction> getShutdownActions() {
        List<ShutdownAction> ret = new ArrayList<ShutdownAction>(shutdownActions);
        Collections.reverse(ret);
        return ret;
    }

    /**
     * Remove a list of shutdown actions
     * @param actions actions to remove
     */
    protected static void removeShutdownActions(List<ShutdownAction> actions) {
        shutdownActions.removeAll(actions);
    }
    // =================================================================================

    // Color init
    private void colorInit() {
        if (color && System.console() != null) {
            AnsiConsole.systemInstall();
            errorHlColor = "\u001B[0;31m";
            infoHlColor = "\u001B[0;32m";
            resetColor = "\u001B[0;39m";
            warnHlColor = "\u001B[0;33m";
            progressHlColor = "\u001B[0;36m";
        } else {
            errorHlColor = infoHlColor = resetColor = warnHlColor = progressHlColor = "";
        }
    }

    /** {@inheritDoc} */
    public void debug(String message) {
        getLog().debug(LOG_PREFIX + message);
    }
    /** {@inheritDoc} */
    public void info(String info) {
        getLog().info(infoHlColor + LOG_PREFIX + info + resetColor);
    }
    /** {@inheritDoc} */
    public void warn(String warn) {
        getLog().warn(warnHlColor + LOG_PREFIX + warn + resetColor);
    }
    /** {@inheritDoc} */
    public boolean isDebugEnabled() {
        return getLog().isDebugEnabled();
    }
    /** {@inheritDoc} */
    public void error(String error) {
        getLog().error(errorHlColor + error + resetColor);
    }

    int oldProgress = 0;
    int total = 0;

    /** {@inheritDoc} */
    public void progressStart(int t) {
        System.out.print(progressHlColor + "       ");
        oldProgress = 0;
        total = t;
    }

    /** {@inheritDoc} */
    public void progressUpdate(int current) {
        System.out.print("=");
        int newProgress = (current * 10 + 5) / total;
        if (newProgress > oldProgress) {
            System.out.print(" " + newProgress + "0% ");
            oldProgress = newProgress;
        }
        System.out.flush();
    }

    /** {@inheritDoc} */
    public void progressFinished() {
        System.out.println(resetColor);
        oldProgress = 0;
        total = 0;
    }
}
