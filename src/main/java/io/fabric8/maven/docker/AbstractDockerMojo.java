package io.fabric8.maven.docker;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import io.fabric8.maven.docker.access.*;
import io.fabric8.maven.docker.access.hc.DockerAccessWithHcClient;
import io.fabric8.maven.docker.config.ConfigHelper;
import io.fabric8.maven.docker.service.QueryService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.service.ServiceHubFactory;
import io.fabric8.maven.docker.util.*;
import org.apache.maven.execution.MavenSession;
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
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.DockerMachineConfiguration;
import io.fabric8.maven.docker.config.VolumeConfiguration;
import io.fabric8.maven.docker.config.handler.ImageConfigResolver;
import io.fabric8.maven.docker.log.LogDispatcher;
import io.fabric8.maven.docker.log.LogOutputSpecFactory;

/**
 * Base class for this plugin.
 *
 * @author roland
 * @since 26.03.14
 */
public abstract class AbstractDockerMojo extends AbstractMojo implements Contextualizable, ConfigHelper.Customizer {

    // Key for indicating that a "start" goal has run
    public static final String CONTEXT_KEY_START_CALLED = "CONTEXT_KEY_DOCKER_START_CALLED";

    // Key holding the log dispatcher
    public static final String CONTEXT_KEY_LOG_DISPATCHER = "CONTEXT_KEY_DOCKER_LOG_DISPATCHER";

    // Key under which the build timestamp is stored so that other mojos can reuse it
    public static final String CONTEXT_KEY_BUILD_TIMESTAMP = "CONTEXT_KEY_BUILD_TIMESTAMP";

    // Key for the previously used image cache
    public static final String CONTEXT_KEY_PREVIOUSLY_PULLED = "CONTEXT_KEY_PREVIOUSLY_PULLED";

    // Minimal API version, independent of any feature used
    public static final String API_VERSION = "1.18";

    // Filename for holding the build timestamp
    public static final String DOCKER_BUILD_TIMESTAMP = "docker/build.timestamp";

    // Current maven project
    @Parameter(defaultValue= "${project}", readonly = true)
    protected MavenProject project;

    // Settings holding authentication info
    @Parameter(defaultValue = "${settings}", readonly = true)
    protected Settings settings;

    // Current maven project
    @Parameter(property= "session")
    protected MavenSession session;

    // Current mojo execution
    @Parameter(property= "mojoExecution")
    protected MojoExecution execution;

    // Handler for external configurations
    @Component
    protected ImageConfigResolver imageConfigResolver;

    @Component
    protected ServiceHubFactory serviceHubFactory;

    @Parameter(property = "docker.autoPull", defaultValue = "on")
    protected String autoPull;

    // Whether to keep the containers afters stopping (start/watch/stop)
    @Parameter(property = "docker.keepContainer", defaultValue = "false")
    protected boolean keepContainer;

    // Whether to remove volumes when removing the container (start/watch/stop)
    @Parameter(property = "docker.removeVolumes", defaultValue = "false")
    protected boolean removeVolumes;

    @Parameter(property = "docker.apiVersion")
    private String apiVersion;

    /**
     * URL to docker daemon
     */
    @Parameter(property = "docker.host")
    private String dockerHost;

    @Parameter(property = "docker.certPath")
    private String certPath;

    // Whether to use color
    @Parameter(property = "docker.useColor", defaultValue = "true")
    protected boolean useColor;

    // For verbose output
    @Parameter(property = "docker.verbose", defaultValue = "false")
    protected boolean verbose;

    // The date format to use when putting out logs
    @Parameter(property = "docker.logDate")
    private String logDate;

    // Log to stdout regardless if log files are configured or not
    @Parameter(property = "docker.logStdout", defaultValue = "false")
    private boolean logStdout;

    // Whether to skip docker altogether
    @Parameter(property = "docker.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Whether the usage of docker machine should be skipped competely
     */
    @Parameter(property = "docker.skip.machine", defaultValue = "false")
    private boolean skipMachine;

    /**
     * Whether to restrict operation to a single image. This can be either
     * the image or an alias name. It can also be comma separated list.
     * This parameter has to be set via the command line s system property.
     */
    @Parameter(property = "docker.filter")
    private String filter;

    // Default registry to use if no registry is specified
    @Parameter(property = "docker.registry")
    protected String registry;

    /**
     * Skip extended authentication
     */
    @Parameter(property = "docker.skip.extendedAuth", defaultValue = "false")
    protected boolean skipExtendedAuth;

    // maximum connection to use in parallel for connecting the docker host
    @Parameter(property = "docker.maxConnections", defaultValue = "100")
    private int maxConnections;

    // Authentication information
    @Parameter
    Map authConfig;

    /**
     * Volume configuration
     */
    @Parameter
    private List<VolumeConfiguration> volumes;

    /**
     * Image configurations configured directly.
     */
    @Parameter
    private List<ImageConfiguration> images;

    // Docker-machine configuration
    @Parameter
    private DockerMachineConfiguration machine;

    // Images resolved with external image resolvers and hooks for subclass to
    // mangle the image configurations.
    private List<ImageConfiguration> resolvedImages;

    // Handler dealing with authentication credentials
    private AuthConfigFactory authConfigFactory;

    protected Logger log;

    // API version as requested from the client
    private String serverVersion;

    /**
     * Entry point for this plugin. It will set up the helper class and then calls
     * {@link #executeInternal(ServiceHub)}
     * which must be implemented by subclass.
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!skip) {
            log = new AnsiLogger(getLog(), useColor, verbose, !settings.getInteractiveMode(), getLogPrefix());
            authConfigFactory.setLog(log);

            LogOutputSpecFactory logSpecFactory = new LogOutputSpecFactory(useColor, logStdout, logDate);

            // The 'real' images configuration to use (configured images + externally resolved images)
            String minimalApiVersion = initImageConfiguration(getBuildTimestamp());
            DockerAccess access = null;
            try {
                access = createDockerAccess(minimalApiVersion);
                ServiceHub serviceHub = serviceHubFactory.createServiceHub(project, session, access, log, logSpecFactory);
                executeInternal(serviceHub);
            } catch (DockerAccessException exp) {
                log.error("%s", exp.getMessage());
                throw new MojoExecutionException(log.errorMessage(exp.getMessage()), exp);
            } catch (MojoExecutionException exp) {
                log.error("%s", exp.getMessage());
                throw exp;
            } finally {
                if (access != null) {
                    access.shutdown();
                }
            }
        }
    }

    /**
     * Get the current build timestamp. this has either already been created by a previous
     * call or a new current date is created
     * @return timestamp to use
     */
    protected synchronized Date getBuildTimestamp() throws MojoExecutionException {
        Date now = (Date) getPluginContext().get(CONTEXT_KEY_BUILD_TIMESTAMP);
        if (now == null) {
            now = getReferenceDate();
            getPluginContext().put(CONTEXT_KEY_BUILD_TIMESTAMP,now);
        }
        return now;
    }

    // Get the referenc date for the build. By default this is picked up
    // from an existing build date file. If this does not exist, the current date is used.
    protected Date getReferenceDate() throws MojoExecutionException {
        Date referenceDate = EnvUtil.loadTimestamp(getBuildTimestampFile());
        return referenceDate != null ? referenceDate : new Date();
    }

    // used for storing a timestamp
    protected File getBuildTimestampFile() {
        return new File(project.getBuild().getDirectory(), DOCKER_BUILD_TIMESTAMP);
    }

    /**
     * Log prefix to use when doing the logs
     * @return
     */
    protected String getLogPrefix() {
        return AnsiLogger.DEFAULT_LOG_PREFIX;
    }

    // Resolve and customize image configuration
    private String initImageConfiguration(Date buildTimeStamp)  {
        // Resolve images
        resolvedImages = ConfigHelper.resolveImages(
            log,
            images,                  // Unresolved images
            new ConfigHelper.Resolver() {
                    @Override
                    public List<ImageConfiguration> resolve(ImageConfiguration image) {
                        return imageConfigResolver.resolve(image, project, session);
                    }
                },
           filter,                   // A filter which image to process
            this);                     // customizer (can be overwritten by a subclass)

        // Initialize configuration and detect minimal API version
        return ConfigHelper.initAndValidate(resolvedImages, apiVersion, new ImageNameFormatter(project, buildTimeStamp), log);
    }

    // Customization hook for subclasses to influence the final configuration. This method is called
    // before initialization and validation of the configuration.
    @Override
    public List<ImageConfiguration> customizeConfig(List<ImageConfiguration> imageConfigs) {
        return imageConfigs;
    }

    private DockerAccess createDockerAccess(String minimalVersion) throws MojoExecutionException, MojoFailureException {
        DockerAccess access = null;
        if (isDockerAccessRequired()) {
            try {
                DockerConnectionDetector dockerConnectionDetector = createDockerConnectionDetector();
                DockerConnectionDetector.ConnectionParameter connectionParam =
                    dockerConnectionDetector.detectConnectionParameter(dockerHost, certPath);
                String version =  minimalVersion != null ? minimalVersion : API_VERSION;
                access = new DockerAccessWithHcClient("v" + version, connectionParam.getUrl(),
                                                      connectionParam.getCertPath(),
                                                      maxConnections,
                                                      log);
                access.start();
                setDockerHostAddressProperty(connectionParam.getUrl());
                serverVersion = access.getServerApiVersion();
                if (!EnvUtil.greaterOrEqualsVersion(serverVersion,version)) {
                    throw new MojoExecutionException(
                        String.format("Server API version %s is smaller than required API version %s", serverVersion, version));
                }
            }
            catch (IOException e) {
                throw new MojoExecutionException("Cannot create docker access object ", e);
            }
        }
        return access;
    }

    private DockerConnectionDetector createDockerConnectionDetector() {
        return new DockerConnectionDetector(getDockerHostProviders());
    }

    /**
     * Return a list of providers which could delive connection parameters from
     * calling external commands. For this plugin this is docker-machine, but can be overridden
     * to add other config options, too.
     *
     * @return list of providers or <code>null</code> if none are applicable
     */
    protected List<DockerConnectionDetector.DockerHostProvider> getDockerHostProviders() {
        DockerMachineConfiguration config = machine;
        if (config == null) {
            Properties projectProps = project.getProperties();
            if (!skipMachine) {
                if (projectProps.containsKey(DockerMachineConfiguration.DOCKER_MACHINE_NAME_PROP)) {
                    config = new DockerMachineConfiguration(
                        projectProps.getProperty(DockerMachineConfiguration.DOCKER_MACHINE_NAME_PROP),
                        projectProps.getProperty(DockerMachineConfiguration.DOCKER_MACHINE_AUTO_CREATE_PROP));
                }
            }
        }

        List<DockerConnectionDetector.DockerHostProvider> ret = new ArrayList<>();
        ret.add(new DockerMachine(log, config));
        return ret;
    }

   /**
     * Override this if your mojo doesnt require access to a Docker host (like creating and attaching
     * docker tar archives)
     *
     * @return <code >true</code> as the default value
     */
    protected boolean isDockerAccessRequired() {
        return true;
    }

    /**
     * Hook for subclass for doing the real job
     *
     * @param serviceHub context for accessing backends
     */
    protected abstract void executeInternal(ServiceHub serviceHub)
        throws DockerAccessException, MojoExecutionException;

    // =============================================================================================

    /**
     * Get all images to use. Can be restricted via -Ddocker.image to pick a one or more images.
     * The values are taken as comma separated list.
     *
     * @return list of image configuration to be use. Can be empty but never null.
     */
    protected List<ImageConfiguration> getResolvedImages() {
        return resolvedImages;
    }

    /**
     * Get all volumes which are defined separately from the images
     *
     * @return defined volumes
     */
    protected List<VolumeConfiguration> getVolumes() {
        return volumes;
    }

    // Registry for managed containers
    private void setDockerHostAddressProperty(String dockerUrl) throws MojoFailureException {
        Properties props = project.getProperties();
        if (props.getProperty("docker.host.address") == null) {
            final String host;
            try {
                URI uri = new URI(dockerUrl);
                if (uri.getHost() == null && (uri.getScheme().equals("unix") || uri.getScheme().equals("npipe"))) {
                    host = "localhost";
                } else {
                    host = uri.getHost();
                }
            } catch (URISyntaxException e) {
                throw new MojoFailureException("Cannot parse " + dockerUrl + " as URI: " + e.getMessage(), e);
            }
            props.setProperty("docker.host.address", host == null ? "" : host);
        }
    }

    // =================================================================================

    @Override
    public void contextualize(Context context) throws ContextException {
        authConfigFactory = new AuthConfigFactory((PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY));
    }

    // =================================================================================

    protected PomLabel getPomLabel() {
        // Label used for this run
        return new PomLabel(project.getGroupId(),project.getArtifactId(),project.getVersion());
    }


    protected AuthConfig prepareAuthConfig(ImageName image, String configuredRegistry, boolean isPush)
            throws MojoExecutionException {
        String user = isPush ? image.getUser() : null;
        String registry = image.getRegistry() != null ? image.getRegistry() : configuredRegistry;

        return authConfigFactory.createAuthConfig(isPush, skipExtendedAuth, authConfig, settings, user, registry);
    }

    protected LogDispatcher getLogDispatcher(ServiceHub hub) {
        LogDispatcher dispatcher = (LogDispatcher) getPluginContext().get(CONTEXT_KEY_LOG_DISPATCHER);
        if (dispatcher == null) {
            dispatcher = new LogDispatcher(hub.getDockerAccess());
            getPluginContext().put(CONTEXT_KEY_LOG_DISPATCHER, dispatcher);
        }
        return dispatcher;
    }

    /**
     * Try to get the registry from various configuration parameters
     *
     * @param imageConfig image config which might contain the registry
     * @param specificRegistry specific registry
     * @return the registry found or null if none could be extracted
     */
    protected String getConfiguredRegistry(ImageConfiguration imageConfig, String specificRegistry) {
        return EnvUtil.findRegistry(imageConfig.getRegistry(), specificRegistry, registry);
    }

    /**
     * Check an image, and, if <code>autoPull</code> is set to true, fetch it. Otherwise if the image
     * is not existent, throw an error
     *  @param hub access object to lookup an image (if autoPull is enabled)
     * @param image image name
     * @param registry optional registry which is used if the image itself doesn't have a registry.
     * @param autoPullAlwaysAllowed whether an unconditional autopull is allowed.
     * @throws DockerAccessException
     * @throws MojoExecutionException
     */
    protected void checkImageWithAutoPull(ServiceHub hub, String image, String registry,
                                          boolean autoPullAlwaysAllowed) throws DockerAccessException, MojoExecutionException {
        // TODO: further refactoring could be done to avoid referencing the QueryService here
        QueryService queryService = hub.getQueryService();
        ImagePullCache previouslyPulledCache = getPreviouslyPulledImageCache();
        if (!queryService.imageRequiresAutoPull(autoPull, image, autoPullAlwaysAllowed, previouslyPulledCache)) {
            return;
        }

        DockerAccess docker = hub.getDockerAccess();
        ImageName imageName = new ImageName(image);
        long time = System.currentTimeMillis();
        docker.pullImage(imageName.getFullName(), prepareAuthConfig(imageName, registry, false), registry);
        log.info("Pulled %s in %s", imageName.getFullName(), EnvUtil.formatDurationTill(time));
        updatePreviousPulledImageCache(image);

        if (registry != null && !imageName.hasRegistry()) {
            // If coming from a registry which was not contained in the original name, add a tag from the
            // full name with the registry to the short name with no-registry.
            docker.tag(imageName.getFullName(registry), image, false);
        }
    }

    private void updatePreviousPulledImageCache(String image) {
        ImagePullCache cache = getPreviouslyPulledImageCache();
        cache.add(image);
        session.getUserProperties().setProperty(CONTEXT_KEY_PREVIOUSLY_PULLED, cache.toString());
    }

    private synchronized ImagePullCache getPreviouslyPulledImageCache() {
        @SuppressWarnings({ "rawtypes", "unchecked" })
            Properties userProperties = session.getUserProperties();
        String pullCacheJson = userProperties.getProperty(CONTEXT_KEY_PREVIOUSLY_PULLED);
        ImagePullCache cache = new ImagePullCache(pullCacheJson);
        if (pullCacheJson == null) {
            userProperties.put(CONTEXT_KEY_PREVIOUSLY_PULLED, cache.toString());
        }
        return cache;
    }

}
