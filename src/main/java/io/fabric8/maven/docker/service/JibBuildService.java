package io.fabric8.maven.docker.service;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.TarImage;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.assembly.AssemblyFiles;
import io.fabric8.maven.docker.assembly.BuildDirs;
import io.fabric8.maven.docker.config.ArchiveCompression;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.JibServiceUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.fabric8.maven.docker.util.JibServiceUtil.containerFromImageConfiguration;
import static io.fabric8.maven.docker.util.JibServiceUtil.getBaseImage;

public class JibBuildService {
    private static final String DOCKER_LOGIN_DEFAULT_REGISTRY = "https://index.docker.io/v1/";
    private static final List<String> DEFAULT_DOCKER_REGISTRIES = Arrays.asList(
            "docker.io", "index.docker.io", "registry.hub.docker.com"
    );
    private static final String PUSH_REGISTRY = ".docker.push.registry";
    private static final String ARCHIVE_FILE_NAME = "docker-build";
    private final Logger log;
    private final ServiceHub serviceHub;
    private final MojoParameters mojoParameters;

    public JibBuildService(ServiceHub hub, MojoParameters mojoParameters, Logger log) {
        this.serviceHub = hub;
        this.mojoParameters = mojoParameters;
        this.log = log;
    }

    public void build(String jibImageFormat, ImageConfiguration imageConfig, RegistryService.RegistryConfig registryConfig) throws MojoExecutionException {
        try {
            log.info("[[B]]JIB[[B]] image build started");
            if (imageConfig.getBuildConfiguration().isDockerFileMode()) {
                throw new MojoExecutionException("Dockerfile mode is not supported with JIB build strategy");
            }
            prependRegistry(imageConfig, mojoParameters.getProject().getProperties().getProperty(PUSH_REGISTRY));
            BuildDirs buildDirs = new BuildDirs(imageConfig.getName(), mojoParameters);
            final Credential pullRegistryCredential = getRegistryCredentials(
                    registryConfig, false, imageConfig, log);
            final JibContainerBuilder containerBuilder = containerFromImageConfiguration(jibImageFormat, imageConfig, pullRegistryCredential);

            File dockerTarArchive = getAssemblyTarArchive(imageConfig, serviceHub, mojoParameters, log);

            for (AssemblyConfiguration assemblyConfiguration : imageConfig.getBuildConfiguration().getAssemblyConfigurations()) {
                // TODO: Improve Assembly Manager so that the effective assemblyFileEntries computed can be properly shared
                // the call to DockerAssemblyManager.getInstance().createDockerTarArchive should not be necessary,
                // files should be added using the AssemblyFileEntry list. DockerAssemblyManager, should provide
                // a common way to achieve this so that both the tar builder and any other builder could get a hold of
                // archive customizers, file entries, etc.
                AssemblyFiles assemblyFiles = serviceHub.getDockerAssemblyManager()
                        .getAssemblyFiles(imageConfig.getName(), assemblyConfiguration, mojoParameters, log);
                final Map<File, AssemblyFiles.Entry> files = assemblyFiles
                        .getUpdatedEntriesAndRefresh().stream()
                        .collect(Collectors.toMap(AssemblyFiles.Entry::getDestFile, Function.identity(), (oldV, newV) -> newV));
                JibServiceUtil.copyToContainer(
                        containerBuilder, buildDirs.getOutputDirectory(), buildDirs.getOutputDirectory().getAbsolutePath(), files);
            }

            JibServiceUtil.buildContainer(containerBuilder,
                    TarImage.at(dockerTarArchive.toPath()).named(imageConfig.getName()), log);
            log.info(" %s successfully built", dockerTarArchive.getAbsolutePath());
        } catch (Exception ex) {
            throw new MojoExecutionException("Error when building JIB image", ex);
        }
    }

    public void push(Collection<ImageConfiguration> imageConfigs, int retries, RegistryService.RegistryConfig registryConfig, boolean skipTag) throws MojoExecutionException {
        try {
            for (ImageConfiguration imageConfiguration : imageConfigs) {
                prependRegistry(imageConfiguration, registryConfig.getRegistry());
                log.info("This push refers to: %s", imageConfiguration.getName());
                JibServiceUtil.jibPush(
                        imageConfiguration,
                        getRegistryCredentials(registryConfig, true, imageConfiguration, log),
                        getBuildTarArchive(imageConfiguration, mojoParameters), skipTag,
                        log
                );
            }
        } catch (Exception ex) {
            throw new MojoExecutionException("Error when push JIB image", ex);
        }
    }

    static ImageConfiguration prependRegistry(ImageConfiguration imageConfiguration, String registry) {
        ImageName imageName = new ImageName(imageConfiguration.getName());
        if (!imageName.hasRegistry() && registry != null) {
            imageConfiguration.setName(registry + "/" + imageConfiguration.getName());
            imageConfiguration.setRegistry(registry);
        }
        return imageConfiguration;
    }

    static File getAssemblyTarArchive(ImageConfiguration imageConfig, ServiceHub serviceHub, MojoParameters configuration, Logger log) throws MojoExecutionException {
        log.info("Preparing assembly files");
        final String targetImage = imageConfig.getName();
        return serviceHub
                .getDockerAssemblyManager()
                .createDockerTarArchive(targetImage, configuration, imageConfig.getBuildConfiguration(), log, null);
    }

    static Credential getRegistryCredentials(
            RegistryService.RegistryConfig registryConfig, boolean isPush, ImageConfiguration imageConfiguration, Logger log)
            throws MojoExecutionException {

        String registry;
        if (isPush) {
            registry = EnvUtil.firstRegistryOf(
                    new ImageName(imageConfiguration.getName()).getRegistry(),
                    imageConfiguration.getRegistry(),
                    registryConfig.getRegistry()
            );
        } else {
            registry = EnvUtil.firstRegistryOf(
                    new ImageName(getBaseImage(imageConfiguration)).getRegistry(),
                    registryConfig.getRegistry()
            );
        }
        if (registry == null || DEFAULT_DOCKER_REGISTRIES.contains(registry)) {
            registry = DOCKER_LOGIN_DEFAULT_REGISTRY; // Let's assume docker is default registry.
        }

        AuthConfigFactory authConfigFactory = registryConfig.getAuthConfigFactory();
        AuthConfig standardAuthConfig = authConfigFactory.createAuthConfig(isPush, registryConfig.isSkipExtendedAuth(), registryConfig.getAuthConfig(), registryConfig.getSettings(), null, registry);
        Credential credentials = null;
        if (standardAuthConfig != null) {
            credentials = Credential.from(standardAuthConfig.getUsername(), standardAuthConfig.getPassword());
        }
        return credentials;
    }

    static File getBuildTarArchive(ImageConfiguration imageConfiguration, MojoParameters mojoParameters) {
        BuildDirs buildDirs = new BuildDirs(imageConfiguration.getName(), mojoParameters);
        return new File(buildDirs.getTemporaryRootDirectory(), ARCHIVE_FILE_NAME + "." + ArchiveCompression.none.getFileSuffix());
    }
}