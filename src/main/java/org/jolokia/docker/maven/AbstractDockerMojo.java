package org.jolokia.docker.maven;

import java.util.*;

import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.Parameter;
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

    // URL to docker daemon
    @Parameter(property = "docker.url",defaultValue = "http://localhost:4243")
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

    // Map with image names as keys and containerId as values (more than one if multiple containers
    // for a single image has been started)
    private static final Map<String,Set<String>> containerMap = new HashMap<String, Set<String>>();

    // Set of image ids which should be removed
    private static final Set<String> imageSet = new HashSet<String>();



    /**
     * Unregister all containers and return their IDs
     */
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


    /**
     * Unregister all images and return their IDs
     *
     * @return ids of registered images
     */
    protected static Collection<String> unregisterAllImages() {
        synchronized (imageSet) {
            Collection<String> ret = new HashSet<String>(imageSet);
            imageSet.clear();
            return ret;
        }
    }

    /**
     * Register a container, used for later cleanup
     *
     * @param image image from which the container has been created
     * @param containerId the container id to register
     */
    protected static void registerContainer(String image, String containerId) {
        synchronized (containerMap) {
            Set<String> ids = containerMap.get(image);
            if (ids == null) {
                ids = new HashSet<String>();
                containerMap.put(image,ids);
            }
            ids.add(containerId);
        }
    }

    /**
     * Register and remember image
     *
     * @param image id of image to remember for deletion.
     */
    protected static void registerImage(String image) {
        synchronized (imageSet) {
            imageSet.add(image);
        }
    }

    /**
     * Unregister all containers for a single image and return their ids.
     *
     * @param image the image for which the containers should be unregistered
     *
     * @return id of containers created from this image
     */
    protected Set<String> unregisterContainersOfImage(String image) {
        synchronized (containerMap) {
            return containerMap.remove(image);
        }
    }

    /**
     * Unregister a single image from the registry
     *
     * @param image image to unregister
     */
    protected boolean unregisterImage(String image) {
        synchronized (imageSet) {
            return imageSet.remove(image);
        }
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
