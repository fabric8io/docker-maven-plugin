package io.fabric8.maven.docker;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Date;
import java.util.Enumeration;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.build.maven.MavenBuildContext;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.build.docker.DockerBuildService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.EnvUtil;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Mojo for building a data image
 *
 * @author roland
 * @since 28.07.14
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.INSTALL)
public class BuildMojo extends AbstractBuildSupportMojo {

    public static final String DMP_PLUGIN_DESCRIPTOR = "META-INF/maven/io.fabric8/dmp-plugin";
    public static final String DOCKER_EXTRA_DIR = "docker-extra";

    @Parameter(property = "docker.skip.build", defaultValue = "false")
    protected boolean skipBuild;

    @Parameter(property = "docker.name", defaultValue = "")
    protected String name;

    /**
     * Skip building tags
     */
    @Parameter(property = "docker.skip.tag", defaultValue = "false")
    protected boolean skipTag;

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
            if(buildConfig.skip()) {
                log.info("%s : Skipped building", aImageConfig.getDescription());
            } else {
                buildAndTag(hub, aImageConfig);
            }
        }
    }

    protected void buildAndTag(ServiceHub hub, ImageConfiguration imageConfig)
            throws MojoExecutionException, IOException {

        EnvUtil.storeTimestamp(getBuildTimestampFile(), getBuildTimestamp());

        MavenBuildContext buildContext = getBuildContext(hub.getArchiveService());
        DockerBuildService buildService = hub.getBuildService();

        buildService.buildImage(imageConfig, buildContext, buildArgs);
        if (!skipTag) {
            buildService.tagImage(imageConfig.getName(), imageConfig);
        }
    }

    // We ignore an already existing date file and always return the current date
    @Override
    protected Date getReferenceDate() {
        return new Date();
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
            log.verbose("Found dmp-plugin %s but could not be called : %s",
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
            log.verbose("Build plugin %s does not support 'addExtraFiles' method", buildPluginClass);
        }
    }

}
