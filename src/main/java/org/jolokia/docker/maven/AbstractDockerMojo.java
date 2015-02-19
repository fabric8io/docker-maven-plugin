package org.jolokia.docker.maven;

import java.io.File;
import java.util.*;

import org.apache.maven.plugin.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import org.jolokia.docker.maven.access.*;
import org.jolokia.docker.maven.config.*;
import org.jolokia.docker.maven.config.handler.ImageConfigResolver;
import org.jolokia.docker.maven.log.ContainerLogOutputSpec;
import org.jolokia.docker.maven.log.LogDispatcher;
import org.jolokia.docker.maven.util.*;

import static org.fusesource.jansi.Ansi.Color.*;
import static org.fusesource.jansi.Ansi.ansi;

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

    // Key holding the log dispatcher
    public static final Object CONTEXT_KEY_LOG_DISPATCHER = "CONTEXT_KEY_DOCKER_LOG_DISPATCHER";

    // Standard HTTPS port (IANA registered). The other 2375 with plain HTTP is used only in older
    // docker installations.
    public static final String DOCKER_HTTPS_PORT = "2376";

    // Current maven project
    /** @parameter default-value="${project}" */
    protected MavenProject project;

    // Settings holding authentication info
    /** @component */
    protected Settings settings;

    // Handler for external configurations
    /** @component */
    protected ImageConfigResolver imageConfigResolver;

    /** @parameter property = "docker.autoPull" default-value = "true" */
    protected boolean autoPull;

    /** @parameter property = "docker.apiVersion" default-value = "v1.15" */
    private String apiVersion;
    
    // URL to docker daemon
    /** @parameter property = "docker.host" */
    private String dockerHost;

    /** @parameter property = "docker.certPath" */
    private String certPath;

    // If logging is enabled globally

    // Whether to use color
    /** @parameter property = "docker.useColor" default-value = "true" */
    protected boolean useColor;

    // The date format to use when putting out logs
    /** @parameter property = "docker.logDate" */
    private String logDate;

    // Whether to skip docker altogether
    /** @parameter property = "docker.skip" default-value = "false" */
    private boolean skip;

    // Whether to restrict operation to a single image. This can be either
    // the image or an alias name. It can also be comma separated list.
    // This parameter is typically set via the command line.
    /** @parameter property = "docker.image" */
    private String image;

    // Default registry to use if no registry is specified
    /** @parameter property = "docker.registry" */
    private String registry;

    // Authentication information
    /** @parameter */
    Map authConfig;

    // Relevant configuration to use. This includes also references to external
    // images
    /**
     * @parameter
     * @required */
    private List<ImageConfiguration> images;

    // ANSI escapes for various colors (or empty strings if no coloring is used)
    private Ansi.Color
            COLOR_ERROR = RED,
            COLOR_INFO = GREEN,
            COLOR_WARNING = YELLOW,
            COLOR_PROGRESS = CYAN;

    // Handler dealing with authentication credentials
    private AuthConfigFactory authConfigFactory;

    /**
     * Entry point for this plugin. It will set up the helper class and then calls {@link #executeInternal(DockerAccess)}
     * which must be implemented by subclass.
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!skip) {
            colorInit();
            DockerAccess access;
            try {
                access = new DockerAccessWithHttpClient(apiVersion, extractUrl(), getCertPath(), this);
                access.start();
            } catch (DockerAccessException e) {
                throw new MojoExecutionException("Cannot create docker access object ",e);
            }
            try {
                executeInternal(access);
            } catch (DockerAccessException exp) {
                throw new MojoExecutionException(ansi().fg(COLOR_ERROR).a(exp.getMessage()).reset().toString(), exp);
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
            throw new IllegalArgumentException("No url given and no DOCKER_HOST environment variable set");
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
        for (ImageConfiguration imageConfig : resolvedImages) {
            if (matchesConfiguredImages(this.image, imageConfig)) {
                ret.add(imageConfig);
            }
        }
        return ret;
    }

    private List<ImageConfiguration> resolveImages() {
        List<ImageConfiguration> ret = new ArrayList<>();
        for (ImageConfiguration image : images) {
            ret.addAll(imageConfigResolver.resolve(image,project.getProperties()));
        }
        return ret;
    }

    // Check if the provided image configuration matches the given
    protected boolean matchesConfiguredImages(String imageList, ImageConfiguration imageConfig) {
        if (imageList == null) {
            return true;
        }
        Set<String> imagesAllowed = new HashSet<>(Arrays.asList(imageList.split("\\s*,\\s*")));
        return imagesAllowed.contains(imageConfig.getName()) || imagesAllowed.contains(imageConfig.getAlias());
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
        if (System.console() == null) {
            useColor = false;
        }
        if (useColor) {
            AnsiConsole.systemInstall();
            Ansi.setEnabled(true);
        } else {
            Ansi.setEnabled(false);
        }
    }

    /** {@inheritDoc} */
    public void debug(String message) {
        getLog().debug(LOG_PREFIX + message);
    }
    /** {@inheritDoc} */
    public void info(String info) {
        getLog().info(ansi().fg(COLOR_INFO).a(LOG_PREFIX).a(info).reset().toString());
    }
    /** {@inheritDoc} */
    public void warn(String warn) {
        getLog().warn(ansi().fg(COLOR_WARNING).a(LOG_PREFIX).a(warn).reset().toString());
    }
    /** {@inheritDoc} */
    public boolean isDebugEnabled() {
        return getLog().isDebugEnabled();
    }
    /** {@inheritDoc} */
    public void error(String error) {
        getLog().error(ansi().fg(COLOR_ERROR).a(error).reset().toString());
    }

    private int oldProgress = 0;
    private int total = 0;

    // A progress indicator is always written out to standard out if a tty is enabled.

    /** {@inheritDoc} */
    public void progressStart(int t) {
        if (getLog().isInfoEnabled()) {
            print(ansi().fg(COLOR_PROGRESS) + "       ");
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
            println(ansi().reset().toString());
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

    protected static String toContainerAndImageDescription(String container, String description) {
        return container.substring(0, 12) + " " + description;
    }

    protected LogDispatcher getLogDispatcher(DockerAccess docker) {
        LogDispatcher dispatcher = (LogDispatcher) getPluginContext().get(CONTEXT_KEY_LOG_DISPATCHER);
        if (dispatcher == null) {
            dispatcher = new LogDispatcher(docker,useColor);
            dispatcher.addLogOutputStream(System.out);
            getPluginContext().put(CONTEXT_KEY_LOG_DISPATCHER,dispatcher);
        }
        return dispatcher;
    }

    protected ContainerLogOutputSpec getContainerLogSpec(String containerId, ImageConfiguration imageConfiguration) {
        ContainerLogOutputSpec.Builder builder = new ContainerLogOutputSpec.Builder();
        LogConfiguration logConfig = extractLogConfiguration(imageConfiguration);

        addLogFormat(builder, logConfig);
        addPrefix(builder, logConfig.getPrefix(), imageConfiguration.getAlias(), containerId);

        builder.containerId(containerId)
                .color(logConfig.getColor());

        return builder.build();
    }

    private void addPrefix(ContainerLogOutputSpec.Builder builder, String logPrefix, String alias, String containerId) {
        String prefix = logPrefix;
        if (prefix == null) {
            prefix = alias;
        }
        if (prefix == null) {
            prefix = containerId.substring(0,6);
        }
        builder.prefix(prefix);
    }

    private void addLogFormat(ContainerLogOutputSpec.Builder builder, LogConfiguration logConfig) {
        String logFormat = logConfig.getDate() != null ? logConfig.getDate() : logDate;
        if (logFormat != null && logFormat.equalsIgnoreCase("true")) {
            logFormat = "DEFAULT";
        }
        if (logFormat != null) {
            builder.timeFormatter(logFormat);
        }
    }

    private LogConfiguration extractLogConfiguration(ImageConfiguration imageConfiguration) {
        RunImageConfiguration runConfig = imageConfiguration.getRunConfiguration();
        LogConfiguration logConfig = null;
        if (runConfig != null) {
            logConfig = runConfig.getLog();
        }
        if (logConfig == null) {
            logConfig = LogConfiguration.DEFAULT;
        }
        return logConfig;
    }

    /**
     * Try to get the registry from various parameters
     *
     * @param imageConfig image config which might contain the registry
     * @return the registry found or null if none could be extracted
     */
    protected String getRegistry(ImageConfiguration imageConfig) {
        return EnvUtil.findRegistry(imageConfig.getRegistry(),registry);
    }

    /**
     * Check an image, and, if <code>autoPull</code> is set to true, fetch it. Otherwise if the image
     * is not existant, throw an error
     *
     * @param docker access object to lookup an image (if autoPull is enabled)
     * @param name image name
     * @param registry optional registry which is used if the image itself doesn't have a registry.
     *
     * @throws DockerAccessException
     * @throws MojoExecutionException
     */
    protected void checkImageWithAutoPull(DockerAccess docker, String name, String registry) throws DockerAccessException, MojoExecutionException {
        if (!docker.hasImage(name)) {
            if (autoPull) {
                docker.pullImage(name, prepareAuthConfig(name), registry);
                ImageName imageName = new ImageName(name);
                if (registry != null && !imageName.hasRegistry()) {
                    // If coming from a registry which was not contained in the original name, add a tag from the
                    // short name with no-registry to the full name with the registry.
                    docker.tag(imageName.getFullNameWithTag(registry), name, false);
                }
            }
            else {
                throw new MojoExecutionException(this, "No image '" + name + "' found", "Please enable 'autoPull' or pull image '" + name
                        + "' yourself (docker pull " + name + ")");
            }
        }
    }

}
