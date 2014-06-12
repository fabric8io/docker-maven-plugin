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

    // Remember data created during start
    private static class ImageStartData {
        private String image;
        private String container;
        private String dataImage;
        private String dataContainer;

        private ImageStartData(String image, String container, String dataImage, String dataContainer) {
            this.image = image;
            this.container = container;
            this.dataImage = dataImage;
            this.dataContainer = dataContainer;
        }
    }

    // Map with image names as keys and containerId as values (more than one if multiple containers
    // for a single image has been started)
    private static final Map<String,Set<ImageStartData>> containerMap = new HashMap<String, Set<ImageStartData>>();

    /**
     * Register a container, used for later cleanup
     *  @param image image from which the container has been created
     * @param containerId the container id to register
     * @param dataImage
     * @param dataContainerId
     */
    protected static void registerStartData(String image, String containerId, String dataImage, String dataContainerId) {
        synchronized (containerMap) {
            Set<ImageStartData> ids = containerMap.get(image);
            if (ids == null) {
                ids = new HashSet<ImageStartData>();
                containerMap.put(image,ids);
            }
            ids.add(new ImageStartData(image, containerId, dataImage, dataContainerId));
        }
    }

    /**
     * Unregister all containers for a single image and return their ids.
     *
     * @param image the image for which the containers should be unregistered
     *
     * @return id of containers created from this image
     */
    protected Set<String> getContainersForImage(String image) throws MojoFailureException {
        synchronized (containerMap) {
            Set<ImageStartData> dataSet = extractDataForImage(image);
            Set<String> iIds = extractContainerIds(dataSet);
            if (iIds == null) {
                throw new MojoFailureException("No container id given");
            }
            return iIds;
        }
    }

    protected Set<String> getDataImagesForImage(String image) {
        synchronized (containerMap) {
            Set<ImageStartData> dataSet = extractDataForImage(image);
            Set<String> imageIds = new HashSet<String>();
            for (ImageStartData data : dataSet) {
                if (data.dataImage != null) {
                    imageIds.add(data.dataImage);
                }
            }
            return imageIds;
        }
    }

    private Set<ImageStartData> extractDataForImage(String image) {
        Set<ImageStartData> dataSet = new HashSet<ImageStartData>();
        if (image != null) {
            if (containerMap.containsKey(image)) {
                dataSet.addAll(containerMap.get(image));
            }
        } else {
            for (Map.Entry<String,Set<ImageStartData>> entry : containerMap.entrySet()) {
                dataSet.addAll(entry.getValue());
            }
        }
        return dataSet;
    }

    private Set<String> extractContainerIds(Set<ImageStartData> pDataSet) {
        Set<String> containerIds = new HashSet<String>();
        for (ImageStartData data : pDataSet) {
            containerIds.add(data.container);
        }
        return containerIds;
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
