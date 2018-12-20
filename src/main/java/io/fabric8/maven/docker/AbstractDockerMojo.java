package io.fabric8.maven.docker;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.ExecException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ConfigHelper;
import io.fabric8.maven.docker.config.DockerMachineConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.RegistryAuthConfiguration;
import io.fabric8.maven.docker.config.VolumeConfiguration;
import io.fabric8.maven.docker.config.handler.ImageConfigResolver;
import io.fabric8.maven.docker.log.LogDispatcher;
import io.fabric8.maven.docker.log.LogOutputSpecFactory;
import io.fabric8.maven.docker.service.DockerAccessFactory;
import io.fabric8.maven.docker.service.ImagePullManager;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.service.ServiceHubFactory;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.GavLabel;
import io.fabric8.maven.docker.util.ImageNameFormatter;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;

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

    @Component
    protected DockerAccessFactory dockerAccessFactory;

    @Parameter
    protected String autoPull;

    @Parameter
    protected String imagePullPolicy;

    // Whether to keep the containers afters stopping (start/watch/stop)
    @Parameter
    protected boolean keepContainer;

    // Whether to remove volumes when removing the container (start/watch/stop)
    @Parameter
    protected boolean removeVolumes;

    @Parameter
    private String apiVersion;

    /**
     * URL to docker daemon
     */
    @Parameter
    private String dockerHost;

    @Parameter
    private String certPath;

    // Whether to use color
    @Parameter
    protected boolean useColor;

    // For verbose output
    @Parameter
    protected boolean verbose;

    // The date format to use when putting out logs
    @Parameter
    private String logDate;

    // Log to stdout regardless if log files are configured or not
    @Parameter
    private boolean logStdout;

    // Whether to skip docker altogether
    @Parameter
    private boolean skip;

    /**
     * Whether the usage of docker machine should be skipped completely
     */
    @Parameter
    private boolean skipMachine;

    /**
     * Whether to restrict operation to a single image. This can be either
     * the image or an alias name. It can also be comma separated list.
     * This parameter has to be set via the command line s system property.
     */
    @Parameter
    private String filter;

    // Default registry to use if no registry is specified
    @Parameter
    protected String registry;

    /**
     * Skip extended authentication
     */
    @Parameter
    protected boolean skipExtendedAuth;

    // maximum connection to use in parallel for connecting the docker host
    @Parameter
    private int maxConnections;

    // Authentication information
    @Parameter
    private RegistryAuthConfiguration authConfig;

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

    private String minimalApiVersion;

    protected String getAutoPull() {
        return getProperty("autoPull");
    }

    protected String getImagePullPolicy() {
        return getProperty("imagePullPolicy");
    }

    protected boolean getKeepContainer() {
        return Boolean.parseBoolean(getProperty("keepContainer", "false"));
    }

    protected boolean getRemoveVolumes() {
        return Boolean.parseBoolean(getProperty("removeVolumes", "false"));
    }

    private String getApiVersion() {
        return getProperty("apiVersion");
    }

    private String getDockerHost() {
        return getProperty("host");
    }

    private String getCertPath() {
        return getProperty("certPath");
    }

    protected boolean getUseColor() {
        return Boolean.parseBoolean(getProperty("useColor", "true"));
    }

    protected boolean getVerbose() {
        return Boolean.parseBoolean(getProperty("verbose", "false"));
    }

    private String getLogDate() {
        return getProperty("logDate");
    }

    private boolean getLogStdout() {
        return Boolean.parseBoolean(getProperty("logStdout", "false"));
    }

    private boolean getSkip() {
        return Boolean.parseBoolean(getProperty("skip", "false"));
    }

    private boolean getSkipMachine() {
        return Boolean.parseBoolean(getProperty("skip.machine", "false"));
    }

    private String getFilter() {
        return getProperty("filter");
    }

    private String getRegistry() {
        return getProperty("registry");
    }

    protected boolean getSkipExtendedAuth() {
        return Boolean.parseBoolean(getProperty("skip.extendedAuth", "false"));
    }

    private int getMaxConnections() {
        return Integer.parseInt(getProperty("maxConnections", "100"));
    }

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
        if (!getSkip()) {
            log = new AnsiLogger(getLog(), getUseColor(), getVerbose(), !settings.getInteractiveMode(), getLogPrefix());
            authConfigFactory.setLog(log);
            imageConfigResolver.setLog(log);

            LogOutputSpecFactory logSpecFactory = new LogOutputSpecFactory(getUseColor(), getLogStdout(), getLogDate());

            ConfigHelper.validateExternalPropertyActivation(project, images);

            DockerAccess access = null;
            try {
                // The 'real' images configuration to use (configured images + externally resolved images)
                this.minimalApiVersion = initImageConfiguration(getBuildTimestamp());
                if (isDockerAccessRequired()) {
                    DockerAccessFactory.DockerAccessContext dockerAccessContext = getDockerAccessContext();
                    access = dockerAccessFactory.createDockerAccess(dockerAccessContext);
                }
                ServiceHub serviceHub = serviceHubFactory.createServiceHub(project, session, access, log, logSpecFactory);
                executeInternal(serviceHub);
            } catch (IOException | ExecException exp) {
                logException(exp);
                throw new MojoExecutionException(log.errorMessage(exp.getMessage()), exp);
            } catch (MojoExecutionException exp) {
                logException(exp);
                throw exp;
            } finally {
                if (access != null) {
                    access.shutdown();
                }
            }
        }
    }

    public String getProperty(String propertyName, String defaultValue) {
        String value = getProperty(propertyName);
        return value != null ? value : defaultValue;
    }

    public String getProperty(String propertyName) {
        String key = getPrefix() + propertyName;
        if(System.getProperty(key) != null) {
            return System.getProperty(key);
        }
        if(project.getProperties().getProperty(key) != null) {
            return project.getProperties().getProperty(key);
        }
        return null;
    }


    private void logException(Exception exp) {
        if (exp.getCause() != null) {
            log.error("%s [%s]", exp.getMessage(), exp.getCause().getMessage());
        } else {
            log.error("%s", exp.getMessage());
        }
    }

    protected DockerAccessFactory.DockerAccessContext getDockerAccessContext() {
        return new DockerAccessFactory.DockerAccessContext.Builder()
                .dockerHost(getDockerHost())
                .certPath(getCertPath())
                .machine(machine)
                .maxConnections(getMaxConnections())
                .minimalApiVersion(minimalApiVersion)
                .projectProperties(project.getProperties())
                .skipMachine(getSkipMachine())
                .log(log)
                .build();
    }

    protected RegistryService.RegistryConfig getRegistryConfig(String specificRegistry) throws MojoExecutionException {
        return new RegistryService.RegistryConfig.Builder()
                .settings(settings)
                .authConfig(authConfig != null ? authConfig.toMap() : null)
                .authConfigFactory(authConfigFactory)
                .skipExtendedAuth(getSkipExtendedAuth())
                .registry(specificRegistry != null ? specificRegistry : getRegistry())
                .build();
    }

    /**
     * Get the current build timestamp. this has either already been created by a previous
     * call or a new current date is created
     * @return timestamp to use
     */
    protected synchronized Date getBuildTimestamp() throws IOException {
        Date now = (Date) getPluginContext().get(CONTEXT_KEY_BUILD_TIMESTAMP);
        if (now == null) {
            now = getReferenceDate();
            getPluginContext().put(CONTEXT_KEY_BUILD_TIMESTAMP,now);
        }
        return now;
    }

    // Get the reference date for the build. By default this is picked up
    // from an existing build date file. If this does not exist, the current date is used.
    protected Date getReferenceDate() throws IOException {
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
           getFilter(),                   // A filter which image to process
            this);                     // customizer (can be overwritten by a subclass)

        // Check for simple Dockerfile mode
        File topDockerfile = new File(project.getBasedir(),"Dockerfile");
        if (topDockerfile.exists()) {
            if (resolvedImages.isEmpty()) {
                resolvedImages.add(createSimpleDockerfileConfig(topDockerfile));
            } else if (resolvedImages.size() == 1 && resolvedImages.get(0).getBuildConfiguration() == null) {
                resolvedImages.set(0, addSimpleDockerfileConfig(resolvedImages.get(0), topDockerfile));
            }
        }

        // Initialize configuration and detect minimal API version
        return ConfigHelper.initAndValidate(resolvedImages, getApiVersion(), new ImageNameFormatter(project, buildTimeStamp), log);
    }

    // Customization hook for subclasses to influence the final configuration. This method is called
    // before initialization and validation of the configuration.
    @Override
    public List<ImageConfiguration> customizeConfig(List<ImageConfiguration> imageConfigs) {
        return imageConfigs;
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
        throws IOException, ExecException, MojoExecutionException;

    public abstract String getPrefix();

    // =============================================================================================

    /**
     * Get all images to use. Can be restricted via -Ddocker.filter to pick a one or more images.
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

    // =================================================================================

    @Override
    public void contextualize(Context context) throws ContextException {
        authConfigFactory = new AuthConfigFactory((PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY));
    }

    // =================================================================================

    protected GavLabel getGavLabel() {
        // Label used for this run
        return new GavLabel(project.getGroupId(), project.getArtifactId(), project.getVersion());
    }

    protected LogDispatcher getLogDispatcher(ServiceHub hub) {
        LogDispatcher dispatcher = (LogDispatcher) getPluginContext().get(CONTEXT_KEY_LOG_DISPATCHER);
        if (dispatcher == null) {
            dispatcher = new LogDispatcher(hub.getDockerAccess());
            getPluginContext().put(CONTEXT_KEY_LOG_DISPATCHER, dispatcher);
        }
        return dispatcher;
    }

    public ImagePullManager getImagePullManager(String imagePullPolicy, String autoPull) {
        return new ImagePullManager(getSessionCacheStore(), imagePullPolicy, autoPull);
    }

    private ImagePullManager.CacheStore getSessionCacheStore() {
        return new ImagePullManager.CacheStore() {
            @Override
            public String get(String key) {
                Properties userProperties = session.getUserProperties();
                return userProperties.getProperty(key);
            }

            @Override
            public void put(String key, String value) {
                Properties userProperties = session.getUserProperties();
                userProperties.setProperty(key, value);
            }
        };
    }

    private ImageConfiguration createSimpleDockerfileConfig(File dockerFile) {
        // No configured name, so create one from maven GAV
        String name = EnvUtil.getPropertiesWithSystemOverrides(project).getProperty("docker.name");
        if (name == null) {
            // Default name group/artifact:version (or 'latest' if SNAPSHOT)
            name = "%g/%a:%l";
        }

        BuildImageConfiguration buildConfig =
            new BuildImageConfiguration.Builder()
                .dockerFile(dockerFile.getPath())
                .build();

        return new ImageConfiguration.Builder()
            .name(name)
            .buildConfig(buildConfig)
            .build();
    }

    private ImageConfiguration addSimpleDockerfileConfig(ImageConfiguration image, File dockerfile) {
        BuildImageConfiguration buildConfig =
            new BuildImageConfiguration.Builder()
                .dockerFile(dockerfile.getPath())
                .build();
        return new ImageConfiguration.Builder(image).buildConfig(buildConfig).build();
    }


}
