package io.fabric8.maven.docker;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.ExecException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ConfigHelper;
import io.fabric8.maven.docker.config.DockerMachineConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.RegistryAuthConfiguration;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.docker.config.VolumeConfiguration;
import io.fabric8.maven.docker.config.handler.ImageConfigResolver;
import io.fabric8.maven.docker.log.LogDispatcher;
import io.fabric8.maven.docker.log.LogOutputSpecFactory;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.service.DockerAccessFactory;
import io.fabric8.maven.docker.service.ImagePullManager;
import io.fabric8.maven.docker.service.QueryService;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.service.RegistryService.RegistryConfig;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.service.ServiceHubFactory;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.GavLabel;
import io.fabric8.maven.docker.util.ImageNameFormatter;
import io.fabric8.maven.docker.util.MojoParameters;
import io.fabric8.maven.docker.util.NamePatternUtil;

import io.fabric8.maven.docker.util.ProjectPaths;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenReaderFilter;
import org.apache.maven.shared.utils.logging.MessageUtils;
import org.fusesource.jansi.Ansi;

/**
 * Base class for this plugin.
 *
 * @author roland
 * @since 26.03.14
 */
public abstract class AbstractDockerMojo extends AbstractMojo implements ConfigHelper.Customizer {

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

    // Redirect the plugin output to a file
    @Parameter(property = "outputFile")
    private String outputFile;

    @Parameter(property = "docker.autoPull")
    protected String autoPull;

    @Parameter(property = "docker.imagePullPolicy")
    protected String imagePullPolicy;

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
    @Parameter(property = "docker.verbose")
    protected String verbose;

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

    @Parameter(property = "docker.build.jib", defaultValue = "false")
    public boolean jib;

    @Parameter(property = "docker.build.jib.imageFormat", defaultValue = "docker")
    public String jibImageFormat;

    @Parameter(property = "docker.source.dir", defaultValue="src/main/docker")
    public String sourceDirectory;

    @Parameter(property = "docker.target.dir", defaultValue="target/docker")
    public String outputDirectory;

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
    List<ImageConfiguration> images;

    /**
     * Image configurations configured via maps to allow overriding.
     */
    @Parameter
    private Map<String, ImageConfiguration> imagesMap;

    // Docker-machine configuration
    @Parameter
    private DockerMachineConfiguration machine;

    @Parameter(defaultValue = "${project.packaging}", required = true)
    protected String packaging;

    @Parameter(property = "docker.skip.pom", defaultValue = "false")
    protected boolean skipPom;

    @Parameter( defaultValue = "${reactorProjects}", required = true, readonly = true )
    protected List<MavenProject> reactorProjects;
    @Parameter
    protected MavenArchiveConfiguration archive;

    @Component
    protected MavenFileFilter mavenFileFilter;

    @Component
    protected MavenReaderFilter mavenFilterReader;

    @Parameter
    protected Map<String, String> buildArgs;
    // ==============================================================================================================
    // Parameters required from Maven when building an assembly. They cannot be injected directly
    // into DockerAssemblyCreator.
    // See also here: http://maven.40175.n5.nabble.com/Mojo-Java-1-5-Component-MavenProject-returns-null-vs-JavaDoc-parameter-expression-quot-project-quot-s-td5733805.html
    @Parameter(property = "docker.pull.registry")
    String pullRegistry;

    // Images resolved with external image resolvers and hooks for subclass to
    // mangle the image configurations.
    List<ImageConfiguration> resolvedImages;

    // Handler dealing with authentication credentials
    @Component
    AuthConfigFactory authConfigFactory;

    protected AnsiLogger log;

    private String minimalApiVersion;

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
            boolean ansiRestore = Ansi.isEnabled();
            File output = null;
            if (outputFile != null) {
                output = new File(outputFile);
                if (output.exists()) {
                    output.delete();
                }
            }
            log = new AnsiLogger(getLog(), useColorForLogging(), verbose, !settings.getInteractiveMode(), getLogPrefix(), output);

            try {
                authConfigFactory.setLog(log);
                imageConfigResolver.setLog(log);

                LogOutputSpecFactory logSpecFactory = new LogOutputSpecFactory(useColor, logStdout, logDate);

                ConfigHelper.validateExternalPropertyActivation(project, getAllImages());

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
            } finally {
                Ansi.setEnabled(ansiRestore);
                try {
                    log.close();
                } catch (IOException exp) {
                    logException(exp);
                }
            }
        }
    }

    protected BuildService.BuildContext getBuildContext() {
        return new BuildService.BuildContext.Builder()
          .buildArgs(buildArgs)
          .mojoParameters(createMojoParameters())
          .registryConfig(getRegistryConfig(pullRegistry))
          .build();
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
                .dockerHost(dockerHost)
                .certPath(certPath)
                .machine(machine)
                .maxConnections(maxConnections)
                .minimalApiVersion(minimalApiVersion)
                .projectProperties(project.getProperties())
                .skipMachine(skipMachine)
                .log(log)
                .build();
    }

    protected RegistryService.RegistryConfig getRegistryConfig(String specificRegistry) {
        return new RegistryService.RegistryConfig.Builder()
                .settings(settings)
                .authConfig(authConfig != null ? authConfig.toMap() : null)
                .authConfigFactory(authConfigFactory)
                .skipExtendedAuth(skipExtendedAuth)
                .registry(specificRegistry != null ? specificRegistry : registry)
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

    /**
     * Determine whether to enable colorized log messages
     * @return true if log statements should be colorized
     */
    private boolean useColorForLogging() {
        return useColor && MessageUtils.isColorEnabled()
                && !(EnvUtil.isWindows() && !EnvUtil.isMaven350OrLater(session));
    }

    // Resolve and customize image configuration
    private String initImageConfiguration(Date buildTimeStamp)  {
        // Resolve images
        resolvedImages = ConfigHelper.resolveImages(
            log,
                getAllImages(), // Unresolved images
            new ConfigHelper.Resolver() {
                    @Override
                    public List<ImageConfiguration> resolve(ImageConfiguration image) {
                        return imageConfigResolver.resolve(image, project, session);
                    }
                },
           filter,                   // A filter which image to process
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
        return ConfigHelper.initAndValidate(resolvedImages, apiVersion, new ImageNameFormatter(project, buildTimeStamp), log);
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
        return Boolean.FALSE.equals(jib);
    }

    /**
     * Hook for subclass for doing the real job
     *
     * @param serviceHub context for accessing backends
     */
    protected abstract void executeInternal(ServiceHub serviceHub)
        throws IOException, ExecException, MojoExecutionException;

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

    private List<ImageConfiguration> getAllImages() {
        ArrayList<ImageConfiguration> allImages = new ArrayList<>();
        if (images != null) {
            allImages.addAll(images);
        }
        if (imagesMap != null) {
            imagesMap.forEach((alias, config) -> {
                if (config.getAlias() == null) {
                    config.setAlias(alias);
                }
                allImages.add(config);
            });
        }
        return Collections.unmodifiableList(allImages);
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

    protected boolean invokedTogetherWithDockerStart() {
        Boolean startCalled = (Boolean) getPluginContext().get(CONTEXT_KEY_START_CALLED);
        return startCalled != null && startCalled;
    }

    protected Matcher getImageNameMatcher(String pattern, String configName) throws MojoExecutionException {
        try {
            String nameRegex = NamePatternUtil.convertNamePatternList(pattern, NamePatternUtil.IMAGE_FIELD, true);
            if (nameRegex == null) {
                log.debug("No image name patterns in %s %s", configName, pattern);
                return null;
            }
            log.debug("Converted %s %s into image name regular expression %s", configName, pattern, nameRegex);
            return Pattern.compile(nameRegex).matcher("");
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    protected Matcher getContainerNameMatcher(String pattern, String configName) throws MojoExecutionException {
        try {
            String nameRegex = NamePatternUtil.convertNamePatternList(pattern, NamePatternUtil.NAME_FIELD, true);
            if (nameRegex == null) {
                log.debug("No container name patterns in %s %s", configName, pattern);
                return null;
            }
            log.debug("Converted %s %s into container name regular expression %s", configName, pattern, nameRegex);
            return Pattern.compile(nameRegex).matcher("");
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    protected List<Container> getContainersForPattern(QueryService queryService, boolean all, Matcher imageNameMatcher,
            Matcher containerNameMatcher, String patternConfigName) throws IOException {
        return queryService.listContainers(all).stream()
                .filter(c -> containerMatchesPattern(c, imageNameMatcher, containerNameMatcher, patternConfigName))
                .collect(Collectors.toList());
    }

    protected void pullImage(RegistryService registryService, ImageConfiguration imageConfig,
                             String pullRegistry) throws MojoExecutionException, DockerAccessException {
        String imageName = imageConfig.getName();
        RunImageConfiguration runConfiguration = imageConfig.getRunConfiguration();
        ImagePullManager pullManager = getImagePullManager(determinePullPolicy(runConfiguration), autoPull);
        RegistryConfig registryConfig = getRegistryConfig(pullRegistry);
        registryService.pullImageWithPolicy(imageName, pullManager, registryConfig, imageConfig.getBuildConfiguration());
    }

    protected boolean shouldSkipPom() {
        return skipPom && packaging.equalsIgnoreCase("pom");
    }

    private boolean containerMatchesPattern(Container container, Matcher imageNameMatcher, Matcher containerNameMatcher,
            String patternConfigName) {
        if (imageNameMatcher != null && container.getImage() != null && imageNameMatcher.reset(container.getImage())
                .find()) {
            log.debug("Container image %s matched %s", container.getImage(), patternConfigName);
            return true;
        } else if (containerNameMatcher != null && container.getName() != null && containerNameMatcher
                .reset(container.getName()).find()) {
            log.debug("Container name %s matched %s", container.getName(), patternConfigName);
            return true;
        } else {
            log.debug("Neither container image %s nor name %s matched %s", container.getImage(), container.getName(),
                    patternConfigName);
            return false;
        }
    }

    private String determinePullPolicy(RunImageConfiguration runConfig) {
        return runConfig.getImagePullPolicy() != null ? runConfig.getImagePullPolicy() : imagePullPolicy;
    }

    protected ProjectPaths createProjectPaths() {
        return new ProjectPaths(project.getBasedir(), outputDirectory);
    }

    protected MojoParameters createMojoParameters() {
        return new MojoParameters(session, project, archive, mavenFileFilter, mavenFilterReader,
            settings, sourceDirectory, outputDirectory, reactorProjects);
    }
}
