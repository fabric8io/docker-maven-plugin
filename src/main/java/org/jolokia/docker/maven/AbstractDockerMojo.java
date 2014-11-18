package org.jolokia.docker.maven;

import java.io.File;
import java.util.*;

import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.fusesource.jansi.AnsiConsole;
import org.jolokia.docker.maven.access.*;
import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.config.handler.ImageConfigResolver;
import org.jolokia.docker.maven.util.*;

/**
 * Base class for this plugin.
 *
 * @author roland
 * @since 26.03.14
 */
public abstract class AbstractDockerMojo extends AbstractMojo implements LogHandler,Contextualizable {

    // prefix used for console output
    private static final String LOG_PREFIX = "DOCKER> ";

    // Key in plugin context specifying shutdown actions
    public static final String CONTEXT_KEY_SHUTDOWN_ACTIONS = "CONTEXT_KEY_DOCKER_SHUTDOWN_ACTIONS";

    // Key for indicating that a "start" goal has run
    public static final String CONTEXT_KEY_START_CALLED = "CONTEXT_KEY_DOCKER_START_CALLED";

    // Standard HTTPS port (IANA registered). The other 2375 with plain HTTP is used only in older
    // docker installations.
    public static final String DOCKER_HTTPS_PORT = "2376";

    // Current maven project
    @Component
    protected MavenProject project;

    // Settings holding authentication info
    @Component
    protected Settings settings;

    // Handler for external configurations
    @Component
    protected ImageConfigResolver imageConfigResolver;

    // URL to docker daemon
    @Parameter(property = "docker.host")
    private String dockerHost;

    @Parameter(property = "docker.certPath")
    private String certPath;

    // Whether to use color
    @Parameter(property = "docker.useColor", defaultValue = "true")
    private boolean useColor;

    // Whether to skip docker altogether
    @Parameter(property = "docker.skip", defaultValue = "false")
    private boolean skip;

    // Whether to restrict operation to a single image. This can be either
    // the image or an alias name
    @Parameter(property = "docker.image")
    private String image;

    // Authentication information
    @Parameter
    Map authConfig;

    // Relevant configuration to use. This includes also references to external
    // images
    @Parameter(required = true)
    private List<ImageConfiguration> images;

    // The resolved list of image configurations. This list is internal an will
    // created during startup.
    private ArrayList<ImageConfiguration> resolvedImages;

    // ANSI escapes for various colors (or empty strings if no coloring is used)
    private String errorHlColor,infoHlColor,warnHlColor,resetColor,progressHlColor;

    // Handler dealing with authentication credentials
    private AuthConfigFactory authConfigFactory;

    /**
     * Entry point for this plugin. It will set up the helper class and then calls {@link #executeInternal(DockerAccess)}
     * which must be implemented by subclass.
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!skip) {
            colorInit();
            DockerAccess access = null;
            try {
                access = new DockerAccessWithHttpClient(extractUrl(), getCertPath(), this);
                access.start();
            } catch (DockerAccessException e) {
                throw new MojoExecutionException("Cannot create docker access object ",e);
            }
            try {
                executeInternal(access);
            } catch (DockerAccessException exp) {
                throw new MojoExecutionException(errorHlColor + exp.getMessage() + resetColor, exp);
            } finally {
                access.shutdown();
            }
        }
    }

    private String getCertPath() {
        String path = certPath != null ? certPath : System.getenv("DOCKER_CERT_PATH");
        if (path == null) {
            File dockerHome = new File(System.getProperty("user.home") + "/.docker");
            if (dockerHome.isDirectory() && dockerHome.list(SuffixFileFilter.PEM_FILTER).length > 0) {
                return dockerHome.getAbsolutePath();
            }
        }
        return path;
    }

    // Check both, url and env DOCKER_HOST (first takes precedence)
    private String extractUrl() {
        String connect = dockerHost != null ? dockerHost : System.getenv("DOCKER_HOST");
        if (connect == null) {
            throw new IllegalArgumentException("No url given and now DOCKER_HOST environment variable set");
        }
        String protocol = connect.contains(":" + DOCKER_HTTPS_PORT) ? "https:" : "http:";
        return connect.replaceFirst("^tcp:", protocol);
    }

    /**
     * Hook for subclass for doing the real job
     *
     * @param dockerAccess access object for getting to the DockerServer
     */
    protected abstract void executeInternal(DockerAccess dockerAccess)
            throws DockerAccessException, MojoExecutionException;

    // =============================================================================================
    // Registry for managed containers


    /**
     * Register a shutdown action executed during "stop"
     * @param shutdownAction action to register
     */
    protected void registerShutdownAction(ShutdownAction shutdownAction) {
        getShutdownActions().add(shutdownAction);
    }

    /**
     * Return shutdown actions in reverse registration order
     * @return registered shutdown actions
     */
    protected List<ShutdownAction> getShutdownActionsInExecutionOrder() {
        List<ShutdownAction> ret = new ArrayList<ShutdownAction>(getShutdownActions());
        Collections.reverse(ret);
        return ret;
    }

    /**
     * Remove a list of shutdown actions
     * @param actions actions to remove
     */
    protected void removeShutdownActions(List<ShutdownAction> actions) {
        getShutdownActions().removeAll(actions);
    }

    private Set<ShutdownAction> getShutdownActions() {
        Object obj = getPluginContext().get(CONTEXT_KEY_SHUTDOWN_ACTIONS);
        if (obj == null) {
            Set<ShutdownAction> actions = Collections.synchronizedSet(new LinkedHashSet<ShutdownAction>());
            getPluginContext().put(CONTEXT_KEY_SHUTDOWN_ACTIONS, actions);
            return actions;
        } else {
            return (Set<ShutdownAction>) obj;
        }
    }

    /**
     * Get all images to use. Can be restricted via -Ddocker.image to pick a one or more images. The values
     * is taken as comma separated list.
     *
     * @return list of image configuration to use
     */
    protected List<ImageConfiguration> getImages() {
        List<ImageConfiguration> resolvedImages = resolveImages();
        List<ImageConfiguration> ret = new ArrayList<>();
        for (ImageConfiguration image : resolvedImages) {
            if (matchesConfiguredImages(image)) {
                ret.add(image);
            }
        }
        return ret;
    }

    private List<ImageConfiguration> resolveImages() {
        List<ImageConfiguration> ret = new ArrayList<>();
        for (ImageConfiguration image : images) {
            ret.addAll(imageConfigResolver.resolve(image));
        }
        return ret;
    }

    private boolean matchesConfiguredImages(ImageConfiguration image) {
        if (this.image == null) {
            return true;
        }
        Set<String> imagesAllowed = new HashSet<>(Arrays.asList(this.image.split("\\s*,\\s*")));
        return imagesAllowed.contains(image.getName()) || imagesAllowed.contains(image.getAlias());
    }

    // =================================================================================
    // Extract authentication information

    @Override
    public void contextualize(Context context) throws ContextException {
        authConfigFactory = new AuthConfigFactory((PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY));
    }

    // =================================================================================

     protected String getImageName(String name) {
        if (name != null) {
            return name;
        } else {
            return getDefaultUserName() + "/" + getDefaultRepoName() + ":" + project.getVersion();
        }
    }

    private String getDefaultRepoName() {
        String repoName = project.getBuild().getFinalName();
        if (repoName == null || repoName.length() == 0) {
            repoName = project.getArtifactId();
        }
        return repoName;
    }

    // Repo names with '.' are considered to be remote registries
    private String getDefaultUserName() {
        String groupId = project.getGroupId();
        String repo = groupId.replace('.','_').replace('-','_');
        return repo.length() > 30 ? repo.substring(0,30) : repo;
    }

    // =================================================================================

    // Color init
    private void colorInit() {
        if (useColor && System.console() != null) {
            AnsiConsole.systemInstall();
            errorHlColor = "\u001B[0;31m";
            infoHlColor = "\u001B[0;32m";
            resetColor = "\u001B[0;39m";
            warnHlColor = "\u001B[0;33m";
            progressHlColor = "\u001B[0;36m";
        } else {
            errorHlColor = "";
            infoHlColor = "";
            resetColor = "";
            warnHlColor = "";
            progressHlColor = "";
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

    private int oldProgress = 0;
    private int total = 0;

    // A progress indicator is always written out to standard out if a tty is enabled.

    /** {@inheritDoc} */
    public void progressStart(int t) {
        if (getLog().isInfoEnabled()) {
            print(progressHlColor + "       ");
            oldProgress = 0;
            total = t;
        }
    }

    /** {@inheritDoc} */
    public void progressUpdate(int current) {
        if (getLog().isInfoEnabled()) {
            print("=");
            int newProgress = (current * 10 + 5) / total;
            if (newProgress > oldProgress) {
                print(" " + newProgress + "0% ");
                oldProgress = newProgress;
            }
            flush();
        }
    }


    /** {@inheritDoc} */
    public void progressFinished() {
        if (getLog().isInfoEnabled()) {
            println(resetColor);
            oldProgress = 0;
            total = 0;
        }
    }

    private void println(String txt) {
        System.out.println(txt);
    }

    private void print(String txt) {
        System.out.print(txt);
    }

    private void flush() {
        System.out.flush();
    }

    protected AuthConfig prepareAuthConfig(String image) throws MojoExecutionException {
        return authConfigFactory.createAuthConfig(authConfig, image,settings);
    }

    protected static String getContainerAndImageDescription(String container, String description) {
        return container.substring(0, 12) + " " + description;
    }

    // ==========================================================================================
    // Class for registering a shutdown action

    protected static class ShutdownAction {

        // The image used
        private String image;

        // Alias of the image
        private final String alias;

        // Data container create from image
        private String container;

        // Description
        private String description;

        protected ShutdownAction(ImageConfiguration imageConfig, String container) {
            this.image = imageConfig.getName();
            this.alias = imageConfig.getAlias();
            this.description = imageConfig.getDescription();
            this.container = container;
        }

        /**
         * Check whether this shutdown actions applies to the given image and/or container
         *
         * @param pImage image to check
         * @return true if this action should be applied
         */
        public boolean applies(String pImage) {
            return pImage == null || pImage.equals(image);
        }

        /**
         * Clean up according to the given parameters
         *
         * @param access access object for reaching docker
         * @param log logger to use
         * @param keepContainer whether to keep the container (and its data container)
         */
        public void shutdown(DockerAccess access, LogHandler log,boolean keepContainer)
                throws MojoExecutionException {
            // Stop the container
            try {
                access.stopContainer(container);
                if (!keepContainer) {
                    // Remove the container
                    access.removeContainer(container);
                }
                log.info("Stopped" + (keepContainer ? "" : " and removed") + " container " +
                         getContainerAndImageDescription(container, description));
            } catch (DockerAccessException e) {
                throw new MojoExecutionException("Cannot shutdown",e);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) { return true; }
            if (o == null || getClass() != o.getClass()) {return false;}

            ShutdownAction that = (ShutdownAction) o;

            return container.equals(that.container);

        }

        @Override
        public int hashCode() {
            return container.hashCode();
        }
    }
}
