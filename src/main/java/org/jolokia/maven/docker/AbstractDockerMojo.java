package org.jolokia.maven.docker;

import java.util.*;

import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.Parameter;
import org.fusesource.jansi.AnsiConsole;

/**
 * @author roland
 * @since 26.03.14
 */
abstract public class AbstractDockerMojo extends AbstractMojo implements LogHandler {

    private static final String LOG_PREFIX = "DOCKER> ";

    @Parameter(property = "docker.url",defaultValue = "http://localhost:4243")
    private String url;

    @Parameter(property = "docker.useColor", defaultValue = "false")
    private boolean color;

    private String errorHlColor,infoHlColor,warnHlColor,resetColor,progressHlColor;

    public void execute() throws MojoExecutionException, MojoFailureException {
        init();
        DockerAccess access = new DockerAccess(url.replace("^tcp://", "http://"), this);
        access.start();
        try {
            doExecute(access);
        } catch (MojoExecutionException exp) {
            throw new MojoExecutionException(errorHlColor + exp.getMessage() + resetColor,exp);
        } finally {
            access.shutdown();
        }

    }

    protected abstract void doExecute(DockerAccess dockerAccess) throws MojoExecutionException, MojoFailureException;


    // =============================================================================================
    // Registry for managed containers

    private static final Map<String,Set<String>> containerMap = new HashMap<String, Set<String>>();

    protected static Collection<String> unregisterAllContainer() {
        synchronized (containerMap) {
            Set<String> ret = new HashSet<String>();
            for (String image : containerMap.keySet()) {
                ret.addAll(containerMap.get(image));
            }
            containerMap.clear();
            return ret;
        }
    }

    protected static void registerContainerId(String image, String containerId) {
        synchronized (containerMap) {
            Set<String> ids = containerMap.get(image);
            if (ids == null) {
                ids = new HashSet<String>();
                containerMap.put(image,ids);
            }
            ids.add(containerId);
        }
    }

    protected Set<String> unregisterContainerId(String image) {
        synchronized (containerMap) {
            return containerMap.remove(image);
        }
    }

    // =================================================================================
    private void init() {
        initColorLog();
        if (color) {

            errorHlColor = "\u001B[0;31m";
            infoHlColor = "\u001B[0;32m";
            resetColor = "\u001B[0;39m";
            warnHlColor = "\u001B[0;33m";
            progressHlColor = "\u001B[0;36m";
        } else {
            errorHlColor = infoHlColor = resetColor = warnHlColor = progressHlColor = "";
        }
    }

    public void debug(String message) {
        getLog().debug(LOG_PREFIX + message);
    }

    public void info(String info) {
        getLog().info(infoHlColor + LOG_PREFIX + info + resetColor);
    }

    public void warn(String warn) {
        getLog().warn(warnHlColor + LOG_PREFIX + warn + resetColor);
    }

    public boolean isDebugEnabled() {
        return getLog().isDebugEnabled();
    }

    private void initColorLog() {
        if (color) {
            AnsiConsole.systemInstall();
        }
    }

    public void error(String error) {
        getLog().error(errorHlColor + error + resetColor);
    }


    int oldProgress = 0;

    public void progressStart(int total) {
        System.out.print(progressHlColor + "       ");
        oldProgress = 0;
    }

    public void progressUpdate(int current, int total, long start) {
        System.out.print("=");
        int newProgress = (current * 10 + 5) / total;
        if (newProgress > oldProgress) {
            System.out.print(" " + newProgress + "0% ");
            oldProgress = newProgress;
        }
        System.out.flush();
    }

    public void progressFinished() {
        System.out.println(resetColor);
        oldProgress = 0;
    }

    protected boolean isColor() {
        return color;
    }
}
