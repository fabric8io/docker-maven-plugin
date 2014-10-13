package org.jolokia.docker.maven;

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
import org.jolokia.docker.maven.access.DockerAccess;
import org.jolokia.docker.maven.access.DockerAccessUnirest;
import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.util.*;

/**
 * Base class for this plugin.
 *
 * @author roland
 * @since 26.03.14
 */
public abstract class AbstractDockerMojo extends AbstractMojo implements LogHandler, Contextualizable {

    // prefix used for console output
    private static final String LOG_PREFIX = "DOCKER> ";
    public static final String DOCKER_SHUTDOWN_ACTIONS = "DOCKER_SHUTDOWN_ACTIONS";

    // Current maven project
    @Component
    protected MavenProject project;

    // Settings holding authentication info
    @Component
    protected Settings settings;

    // URL to docker daemon
    @Parameter(property = "docker.url")
    private String url;

    // Whether to use color
    @Parameter(property = "docker.useColor", defaultValue = "true")
    private boolean color;

    // Whether to skip docker alltogether
    @Parameter(property = "docker.skip", defaultValue = "false")
    private boolean skip;

    // Authentication information
    @Parameter
    Map authConfig;

    // Relevant configuration to use
    @Parameter(required = true)
    protected List<ImageConfiguration> images;

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
            DockerAccess access = new DockerAccessUnirest(extractUrl(), this);
            access.start();
            try {
                executeInternal(access);
            } catch (MojoExecutionException exp) {
                throw new MojoExecutionException(errorHlColor + exp.getMessage() + resetColor, exp);
            } finally {
                access.shutdown();
            }
        }
    }

    // Check both, url and env DOCKER_HOST (first takes precedence)
    private String extractUrl() {
        String connect = url != null ? url : System.getenv("DOCKER_HOST");
        if (connect == null) {
            throw new IllegalArgumentException("No url given and now DOCKER_HOST environment variable set");
        }
        return connect.replaceFirst("^tcp:", "http:");
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
        Object obj = getPluginContext().get(DOCKER_SHUTDOWN_ACTIONS);
        if (obj == null) {
            Set<ShutdownAction> actions = Collections.synchronizedSet(new LinkedHashSet<ShutdownAction>());
            getPluginContext().put(DOCKER_SHUTDOWN_ACTIONS, actions);
            return actions;
        } else {
            return (Set<ShutdownAction>) obj;
        }
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
        if (color && System.console() != null) {
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

    protected AuthConfig prepareAuthConfig(String image) throws MojoFailureException {
        return authConfigFactory.createAuthConfig(authConfig, image,settings);
    }

    // ==========================================================================================
    // Class for registering a shutdown action

    protected static class ShutdownAction {

        // The image used
        private String image;

        // Data container create from image
        private String container;

        protected ShutdownAction(String image, String container) {
            this.image = image;
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
            access.stopContainer(container);
            if (!keepContainer) {
                // Remove the container
                access.removeContainer(container);
            }
            log.info("Stopped " + container.substring(0, 12) + (keepContainer ? "" : " and removed") + " container");
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
