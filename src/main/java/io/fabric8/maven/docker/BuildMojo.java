package io.fabric8.maven.docker;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.AuthConfigList;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.service.ImagePullManager;
import io.fabric8.maven.docker.service.JibBuildService;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.DockerFileUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Mojo for building a data image
 *
 * @author roland
 * @since 28.07.14
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.INSTALL, requiresDependencyResolution = ResolutionScope.TEST)
public class BuildMojo extends AbstractBuildSupportMojo {

    public static final String DMP_PLUGIN_DESCRIPTOR = "META-INF/maven/io.fabric8/dmp-plugin";
    public static final String DOCKER_EXTRA_DIR = "docker-extra";

    @Parameter(property = "docker.skip.build", defaultValue = "false")
    protected boolean skipBuild;

    @Parameter(property = "docker.name", defaultValue = "")
    protected String name;

    /**
     * Skip Sending created tarball to docker daemon
     */
    @Parameter(property = "docker.buildArchiveOnly", defaultValue = "false")
    protected String buildArchiveOnly;

    /**
     * Skip building tags
     */
    @Parameter(property = "docker.skip.tag", defaultValue = "false")
    protected boolean skipTag;
    @Parameter(property = "docker.pull.retries", defaultValue = "0")
    private int retries;

    @Override
    protected void executeInternal(ServiceHub hub) throws IOException, MojoExecutionException {
        if (skipBuild) {
            return;
        }

        // Check for build plugins
        executeBuildPlugins();

        // Iterate over all the ImageConfigurations and process one by one
        for (ImageConfiguration imageConfig : getResolvedImages()) {
            processImageConfig(hub, imageConfig);
        }
    }

    protected void buildAndTag(ServiceHub hub, ImageConfiguration imageConfig)
            throws MojoExecutionException, IOException {

        EnvUtil.storeTimestamp(getBuildTimestampFile(), getBuildTimestamp());

        BuildService.BuildContext buildContext = getBuildContext();
        ImagePullManager pullManager = getImagePullManager(determinePullPolicy(imageConfig.getBuildConfiguration()), autoPull);
        proceedWithBuildProcess(hub, buildContext, imageConfig, pullManager);
    }

    private void proceedWithBuildProcess(ServiceHub hub, BuildService.BuildContext buildContext, ImageConfiguration imageConfig, ImagePullManager pullManager) throws MojoExecutionException, IOException {
        if (Boolean.TRUE.equals(jib)) {
            proceedWithJibBuild(hub, buildContext, imageConfig);
        } else {
            proceedWithDockerBuild(hub, buildContext, imageConfig, pullManager);
        }
    }

    private void proceedWithJibBuild(ServiceHub hub, BuildService.BuildContext buildContext, ImageConfiguration imageConfig) throws MojoExecutionException {
        log.info("Building Container image with [[B]]JIB(Java Image Builder)[[B]] mode");
        new JibBuildService(hub, createMojoParameters(), log).build(jibImageFormat, imageConfig, buildContext.getRegistryConfig());
    }

    private void proceedWithDockerBuild(ServiceHub hub, BuildService.BuildContext buildContext, ImageConfiguration imageConfig, ImagePullManager pullManager)
        throws MojoExecutionException, IOException {
        BuildService buildService= hub.getBuildService();
        File buildArchiveFile = buildService.buildArchive(imageConfig, buildContext, resolveBuildArchiveParameter());
        if (Boolean.FALSE.equals(shallBuildArchiveOnly())) {
            if (imageConfig.isBuildX()) {
                hub.getBuildXService().build(createProjectPaths(), imageConfig, null, getAuthConfig(imageConfig), buildArchiveFile);
            } else {
                buildService.buildImage(imageConfig, pullManager, buildContext, buildArchiveFile, retries);
                if (!skipTag) {
                    buildService.tagImage(imageConfig);
                }
            }
        }
    }


    private AuthConfigList getAuthConfig(ImageConfiguration imageConfig) throws MojoExecutionException {
        // TODO: refactor similar code in RegistryService#pushImages
        RegistryService.RegistryConfig registryConfig = getRegistryConfig(pullRegistry);

        ImageName imageName = new ImageName(imageConfig.getName());
        String configuredRegistry = EnvUtil.firstRegistryOf(
            imageName.getRegistry(),
            imageConfig.getRegistry(),
            registryConfig.getRegistry());

        AuthConfig authConfig = registryConfig.createAuthConfig(false, imageName.getUser(), configuredRegistry);
        AuthConfigList authConfigList = new AuthConfigList();
        if (authConfig != null) {
            authConfigList.addAuthConfig(authConfig);
        }

        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
        Set<String> fromRegistries = getRegistriesForPull(buildConfig);
        for (String fromRegistry : fromRegistries) {
            if (configuredRegistry.equalsIgnoreCase(fromRegistry)) {
                continue;
            }
            registryConfig = getRegistryConfig(fromRegistry);
            AuthConfig additionalAuth = registryConfig.createAuthConfig(false, imageName.getUser(), fromRegistry);
            authConfigList.addAuthConfig(additionalAuth);
        }

        return authConfigList;
    }

    private Set<String> getRegistriesForPull(BuildImageConfiguration buildConfig) {
        Set<String> registries = new HashSet<>();
        List<String> fromImages = extractBaseFromDockerfile(buildConfig);
        for (String fromImage : fromImages) {
            ImageName imageName = new ImageName(fromImage);

            if (imageName.hasRegistry()) {
                registries.add(imageName.getRegistry());
            }
        }
        return registries;
    }

    private List<String> extractBaseFromDockerfile(BuildImageConfiguration buildConfig) {
        if (buildConfig.getDockerFile() == null || !buildConfig.getDockerFile().exists()) {
            if (buildConfig.getFrom() != null && !buildConfig.getFrom().isEmpty()) {
                return Collections.singletonList(buildConfig.getFrom());
            }
            return Collections.emptyList();
        }

        List<String> fromImage;
        try {
            MojoParameters mojoParameters = createMojoParameters();
            File fullDockerFilePath = buildConfig.getAbsoluteDockerFilePath(mojoParameters);
            fromImage = DockerFileUtil.extractBaseImages(
                    fullDockerFilePath,
                    DockerFileUtil.createInterpolator(mojoParameters, buildConfig.getFilter()),
                    buildConfig.getArgs());
        } catch (IOException e) {
            return Collections.emptyList();
        }
        return fromImage;
    }

    // We ignore an already existing date file and always return the current date

    @Override
    protected Date getReferenceDate() {
        return new Date();
    }

    private String resolveBuildArchiveParameter() {
        if (buildArchiveOnly != null && !buildArchiveOnly.isEmpty()) {
            if (!(buildArchiveOnly.equalsIgnoreCase("false") ||
                buildArchiveOnly.equalsIgnoreCase("true"))) {
                return buildArchiveOnly;
            }
        }
        return null;
    }

    private boolean shallBuildArchiveOnly() {
        if (buildArchiveOnly != null && !buildArchiveOnly.isEmpty()) {
            if (buildArchiveOnly.equalsIgnoreCase("false") ||
                    buildArchiveOnly.equalsIgnoreCase("true")) {
                return Boolean.parseBoolean(buildArchiveOnly);
            }
        }
        return false;
    }

    private String determinePullPolicy(BuildImageConfiguration buildConfig) {
        return buildConfig != null && buildConfig.getImagePullPolicy() != null ? buildConfig.getImagePullPolicy() : imagePullPolicy;
    }

    /**
     * Helper method to process an ImageConfiguration.
     *
     * @param hub ServiceHub
     * @param aImageConfig ImageConfiguration that would be forwarded to build and tag
     * @throws DockerAccessException
     * @throws MojoExecutionException
     */
    private void processImageConfig(ServiceHub hub, ImageConfiguration aImageConfig) throws IOException, MojoExecutionException {
        BuildImageConfiguration buildConfig = aImageConfig.getBuildConfiguration();

        if (buildConfig != null) {
            if (buildConfig.skip() || shouldSkipPom()) {
                log.info("%s : Skipped building", aImageConfig.getDescription());
            } else {
                buildAndTag(hub, aImageConfig);
            }
        }
    }

    // check for a run-java.sh dependency an extract the script to target/ if found
    private void executeBuildPlugins() {
        try {
            Enumeration<URL> dmpPlugins = Thread.currentThread().getContextClassLoader().getResources(DMP_PLUGIN_DESCRIPTOR);
            while (dmpPlugins.hasMoreElements()) {

                URL dmpPlugin = dmpPlugins.nextElement();
                File outputDir = getAndEnsureOutputDirectory();
                processDmpPluginDescription(dmpPlugin, outputDir);
            }
        } catch (IOException e) {
            log.error("Cannot load dmp-plugins from %s", DMP_PLUGIN_DESCRIPTOR);
        }
    }

    private void processDmpPluginDescription(URL pluginDesc, File outputDir) throws IOException {
        String line = null;
        try (LineNumberReader reader =
                 new LineNumberReader(new InputStreamReader(pluginDesc.openStream(), "UTF8"))) {
            line = reader.readLine();
            while (line != null) {
                if (line.matches("^\\s*#")) {
                    // Skip comments
                    continue;
                }
                callBuildPlugin(outputDir, line);
                line = reader.readLine();
            }
        } catch (ClassNotFoundException e) {
            // Not declared as dependency, so just ignoring ...
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            log.verbose(Logger.LogVerboseCategory.BUILD,"Found dmp-plugin %s but could not be called : %s",
                     line,
                     e.getMessage());
        }
    }

    private File getAndEnsureOutputDirectory() {
        File outputDir = new File(new File(project.getBuild().getDirectory()), DOCKER_EXTRA_DIR);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        return outputDir;
    }

    private void callBuildPlugin(File outputDir, String buildPluginClass) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Class buildPlugin = Class.forName(buildPluginClass);
        try {
            Method method = buildPlugin.getMethod("addExtraFiles", File.class);
            method.invoke(null, outputDir);
            log.info("Extra files from %s extracted", buildPluginClass);
        } catch (NoSuchMethodException exp) {
            log.verbose(Logger.LogVerboseCategory.BUILD,"Build plugin %s does not support 'addExtraFiles' method", buildPluginClass);
        }
    }

}
