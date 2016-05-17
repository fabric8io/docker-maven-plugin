package io.fabric8.maven.docker;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.hc.DockerAccessWithHcClient;
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
import io.fabric8.maven.docker.config.handler.ImageConfigResolver;
import io.fabric8.maven.docker.log.LogDispatcher;
import io.fabric8.maven.docker.log.LogOutputSpecFactory;

/**
 * Base class for this plugin.
 *
 * @author roland
 * @since 26.03.14
 */
public abstract class AbstractDockerMojo extends AbstractMojo implements Contextualizable {

    // Key for indicating that a "start" goal has run
    public static final String CONTEXT_KEY_START_CALLED = "CONTEXT_KEY_DOCKER_START_CALLED";

    // Key holding the log dispatcher
    public static final String CONTEXT_KEY_LOG_DISPATCHER = "CONTEXT_KEY_DOCKER_LOG_DISPATCHER";

    // Standard HTTPS port (IANA registered). The other 2375 with plain HTTP is used only in older
    // docker installations.
    public static final String DOCKER_HTTPS_PORT = "2376";

    // Minimal API version, independent of any feature used
    public static final String API_VERSION = "1.18";

    // Current maven project
    @Parameter(defaultValue= "${project}", readonly = true)
    protected MavenProject project;

    // Settings holding authentication info
    @Parameter(defaultValue = "${settings}", readonly = true)
    protected Settings settings;

    // Current maven project
    @Parameter(defaultValue= "${session}", readonly = true)
    protected MavenSession session;

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

    // URL to docker daemon
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

    // Whether to restrict operation to a single image. This can be either
    // the image or an alias name. It can also be comma separated list.
    // This parameter is typically set via the command line.
    @Parameter(property = "docker.image")
    private String image;

    // Default registry to use if no registry is specified
    @Parameter(property = "docker.registry")
    protected String registry;

    // maximum connection to use in parallel for connecting the docker host
    @Parameter(property = "docker.maxConnections", defaultValue = "100")
    private int maxConnections;

    // property file to write out with port mappings
    @Parameter
    protected String portPropertyFile;
    
    // Authentication information
    @Parameter
    Map authConfig;

    // Relevant images configuration to use. This includes also references to external
    // images
    @Parameter
    private List<ImageConfiguration> images;

    // Handler dealing with authentication credentials
    private AuthConfigFactory authConfigFactory;

    protected Logger log;

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
            log = new AnsiLogger(getLog(), useColor, verbose);
            LogOutputSpecFactory logSpecFactory = new LogOutputSpecFactory(useColor, logStdout, logDate);

            String minimalApiVersion = validateConfiguration(log);
            DockerAccess access = null;
            try {
                access = createDockerAccess(minimalApiVersion);
                ServiceHub serviceHub = serviceHubFactory.createServiceHub(project, session, access, log, logSpecFactory);
                executeInternal(serviceHub);
            } catch (DockerAccessException exp) {
                log.error(exp.getMessage());
                throw new MojoExecutionException(log.errorMessage(exp.getMessage()), exp);
            } finally {
                if (access != null) {
                    access.shutdown();
                }
            }
        }
    }

    private DockerAccess createDockerAccess(String minimalVersion) throws MojoExecutionException, MojoFailureException {
        DockerAccess access = null;
        if (isDockerAccessRequired()) {
            String dockerUrl = EnvUtil.extractUrl(dockerHost);
            try {
                String version =  minimalVersion != null ? minimalVersion : API_VERSION;
                access = new DockerAccessWithHcClient("v" + version, dockerUrl,
                                                      EnvUtil.getCertPath(certPath), maxConnections, log);
                access.start();
                setDockerHostAddressProperty(dockerUrl);
            }
            catch (IOException e) {
                throw new MojoExecutionException("Cannot create docker access object ", e);
            }
        }
        return access;
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

    // Return minimal required version
    private String validateConfiguration(Logger log) {
        String apiVersion = this.apiVersion;
        if (images != null) {
            for (ImageConfiguration imageConfiguration : images) {
                apiVersion = EnvUtil.extractLargerVersion(apiVersion,imageConfiguration.initAndValidate(log));
            }
        }
        return apiVersion;
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
        if (images != null) {
            for (ImageConfiguration image : images) {
                for (ImageConfiguration resolved : imageConfigResolver.resolve(image, project.getProperties())) {
                    resolved.initAndValidate(log);
                    ret.add(resolved);
                }
            }
            verifyImageNames(ret);
        }
        return ret;
    }

    // Extract authentication information
    private void verifyImageNames(List<ImageConfiguration> ret) {
        for (ImageConfiguration config : ret) {
            if (config.getName() == null) {
                throw new IllegalArgumentException("Configuration error: <image> must have a non-null <name>");
            }
        }
    }

    // Check if the provided image configuration matches the given
    protected boolean matchesConfiguredImages(String imageList, ImageConfiguration imageConfig) {
        if (imageList == null) {
            return true;
        }
        Set<String> imagesAllowed = new HashSet<>(Arrays.asList(imageList.split("\\s*,\\s*")));
        return imagesAllowed.contains(imageConfig.getName()) || imagesAllowed.contains(imageConfig.getAlias());
    }

    // Registry for managed containers
    private void setDockerHostAddressProperty(String dockerUrl) throws MojoFailureException {
        Properties props = project.getProperties();
        if (props.getProperty("docker.host.address") == null) {
            final String host;
            try {
                URI uri = new URI(dockerUrl);
                if (uri.getHost() == null && uri.getScheme().equals("unix")) {
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

        return authConfigFactory.createAuthConfig(isPush, authConfig, settings, user, registry);
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
     *
     * @param hub access object to lookup an image (if autoPull is enabled)
     * @param image image name
     * @param registry optional registry which is used if the image itself doesn't have a registry.
     * @param autoPullAlwaysAllowed whether an unconditional autopull is allowed.
     *
     * @throws DockerAccessException
     * @throws MojoExecutionException
     */
    protected void checkImageWithAutoPull(ServiceHub hub, String image, String registry,
            boolean autoPullAlwaysAllowed) throws DockerAccessException, MojoExecutionException {
        // TODO: further refactoring could be done to avoid referencing the QueryService here
        QueryService queryService = hub.getQueryService();
        if (!queryService.imageRequiresAutoPull(autoPull, image, autoPullAlwaysAllowed)) {
            return;
        }

        DockerAccess docker = hub.getDockerAccess();
        ImageName imageName = new ImageName(image);
        docker.pullImage(withLatestIfNoTag(image), prepareAuthConfig(imageName, registry, false), registry);
        if (registry != null && !imageName.hasRegistry()) {
            // If coming from a registry which was not contained in the original name, add a tag from the
            // full name with the registry to the short name with no-registry.
            docker.tag(imageName.getFullName(registry), image, false);
        }
    }

    // Fetch only latest if no tag is given
    private String withLatestIfNoTag(String name) {
        ImageName imageName = new ImageName(name);
        return imageName.getTag() == null ? imageName.getNameWithoutTag() + ":latest" : name;
    }
}
