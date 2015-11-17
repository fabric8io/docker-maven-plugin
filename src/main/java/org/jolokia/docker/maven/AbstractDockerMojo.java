package org.jolokia.docker.maven;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import org.apache.maven.plugin.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.jolokia.docker.maven.access.*;
import org.jolokia.docker.maven.access.hc.DockerAccessWithHcClient;
import org.jolokia.docker.maven.config.ImageConfiguration;
import org.jolokia.docker.maven.config.handler.ImageConfigResolver;
import org.jolokia.docker.maven.log.LogDispatcher;
import org.jolokia.docker.maven.log.LogOutputSpecFactory;
import org.jolokia.docker.maven.service.QueryService;
import org.jolokia.docker.maven.service.ServiceHub;
import org.jolokia.docker.maven.util.*;

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

    public static final String API_VERSION = "v1.18";

    // Current maven project
    /** @parameter default-value="${project}" */
    protected MavenProject project;

    // Settings holding authentication info
    /** @component */
    protected Settings settings;

    // Handler for external configurations
    /** @component */
    protected ImageConfigResolver imageConfigResolver;

    /** @component **/
    protected ServiceHub serviceHub;

    /** @parameter property = "docker.autoPull" default-value = "on" */
    protected String autoPull;

    /**
     * Whether to keep the containers afters stopping (start/watch/stop)
     *
     * @parameter property = "docker.keepContainer" default-value = "false"
     */
    protected boolean keepContainer;

    /**
     * Whether to remove volumes when removing the container (start/watch/stop)
     *
     * @parameter property = "docker.removeVolumes" defaultValue = "false"
     */
    protected boolean removeVolumes;

    /** @parameter property = "docker.apiVersion" */
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

    // For verbose output
    /** @parameter property = "docker.verbose" default-value = "false" */
    protected boolean verbose;

    // The date format to use when putting out logs
    /** @parameter property = "docker.logDate" */
    private String logDate;

    // Log to stdout regardless if log files are configured or not
    /** @parameter property = "docker.logStdout" default-value = "false" */
    private boolean logStdout;

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

    // maximum connection to use in parallel for connecting the docker host
    /** @parameter property = "docker.maxConnections" default-value = "100" */
    private int maxConnections;

    // property file to write out with port mappings
    /** @parameter */
    protected String portPropertyFile;
    
    // Authentication information
    /** @parameter */
    Map authConfig;

    // Relevant configuration to use. This includes also references to external
    // images
    /**
     * @parameter
     */
    private List<ImageConfiguration> images;

    // Handler dealing with authentication credentials
    private AuthConfigFactory authConfigFactory;

    protected Logger log;

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
            log = new AnsiLogger(getLog(), useColor, verbose);

            validateConfiguration(log);

            String dockerUrl = EnvUtil.extractUrl(dockerHost);
            DockerAccess access = createDockerAccess(dockerUrl);
            setDockerHostAddressProperty(dockerUrl);
            serviceHub.init(access,log, new LogOutputSpecFactory(useColor, logStdout, logDate));

            try {
                executeInternal(access);
            } catch (DockerAccessException exp) {
                throw new MojoExecutionException(log.errorMessage(exp.getMessage()), exp);
            } finally {
                access.shutdown();
            }
        }
    }

    private void validateConfiguration(Logger log) {
        if (images != null) {
            for (ImageConfiguration imageConfiguration : images) {
                imageConfiguration.validate(log);
            }
        }
    }

    /**
     * Hook for subclass for doing the real job
     *
     * @param dockerAccess access object for getting to the DockerServer
     */
    protected abstract void executeInternal(DockerAccess dockerAccess)
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
                ret.addAll(imageConfigResolver.resolve(image, project.getProperties()));
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

    private DockerAccess createDockerAccess(String baseUrl) throws MojoExecutionException {
        try {
            String version = (apiVersion == null) ? API_VERSION : apiVersion;
            DockerAccess client = new DockerAccessWithHcClient(version, baseUrl,
                    EnvUtil.getCertPath(certPath), maxConnections, log);
            client.start();

            return client;
        }
        catch (IOException e) {
            throw new MojoExecutionException("Cannot create docker access object ", e);
        }
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


    protected AuthConfig prepareAuthConfig(String image, String configuredRegistry, boolean useUserFromImage) throws MojoExecutionException {
        ImageName name = new ImageName(image);
        String user = useUserFromImage ? name.getUser() : null;
        String registry = name.getRegistry() != null ? name.getRegistry() : configuredRegistry;

        return authConfigFactory.createAuthConfig(authConfig, settings, user, registry);
    }

    protected LogDispatcher getLogDispatcher(DockerAccess docker) {
        LogDispatcher dispatcher = (LogDispatcher) getPluginContext().get(CONTEXT_KEY_LOG_DISPATCHER);
        if (dispatcher == null) {
            dispatcher = new LogDispatcher(docker);
            getPluginContext().put(CONTEXT_KEY_LOG_DISPATCHER, dispatcher);
        }
        return dispatcher;
    }

    /**
     * Try to get the registry from various configuration parameters
     *
     * @param imageConfig image config which might contain the registry
     * @return the registry found or null if none could be extracted
     */
    protected String getConfiguredRegistry(ImageConfiguration imageConfig) {
        return EnvUtil.findRegistry(imageConfig.getRegistry(), registry);
    }

    /**
     * Check an image, and, if <code>autoPull</code> is set to true, fetch it. Otherwise if the image
     * is not existent, throw an error
     *
     * @param docker access object to lookup an image (if autoPull is enabled)
     * @param image image name
     * @param registry optional registry which is used if the image itself doesn't have a registry.
     * @param autoPullAlwaysAllowed whether an unconditional autopull is allowed.
     *
     * @throws DockerAccessException
     * @throws MojoExecutionException
     */
    protected void checkImageWithAutoPull(DockerAccess docker, String image, String registry,
            boolean autoPullAlwaysAllowed) throws DockerAccessException, MojoExecutionException {
        // TODO: further refactoring could be done to avoid referencing the QueryService here
        QueryService queryService = serviceHub.getQueryService();
        if (!queryService.imageRequiresAutoPull(autoPull, image, autoPullAlwaysAllowed)) {
            return;
        }

        docker.pullImage(withLatestIfNoTag(image), prepareAuthConfig(image, registry, false), registry);
        ImageName imageName = new ImageName(image);
        if (registry != null && !imageName.hasRegistry()) {
            // If coming from a registry which was not contained in the original name, add a tag from the
            // short name with no-registry to the full name with the registry.
            docker.tag(imageName.getFullName(registry), image, false);
        }
    }

    // Fetch only latest if no tag is given
    private String withLatestIfNoTag(String name) {
        ImageName imageName = new ImageName(name);
        return imageName.getTag() == null ? imageName.getNameWithoutTag() + ":latest" : name;
    }
}
