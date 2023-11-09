package io.fabric8.maven.docker.service;

import io.fabric8.maven.docker.access.AuthConfigList;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.util.ExternalCommand;
import io.fabric8.maven.docker.assembly.BuildDirs;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.AttestationConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.BuildXConfiguration;
import io.fabric8.maven.docker.config.ConfigHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.ProjectPaths;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static io.fabric8.maven.docker.util.AuthConfigFactory.hasAuthForRegistryInDockerConfig;

public class BuildXService {
    private static final int DOCKER_CLI_BUILDX_CONFIG_COMPATIBLE_MAJOR_VERSION = 23;
    private static final String DOCKER = "docker";
    private final DockerAccess dockerAccess;
    private final DockerAssemblyManager dockerAssemblyManager;
    private final Logger logger;
    private final Exec exec;

    public BuildXService(DockerAccess dockerAccess, DockerAssemblyManager dockerAssemblyManager, Logger logger) {
        this(dockerAccess, dockerAssemblyManager, logger, new DefaultExec(logger));
    }

    public BuildXService(DockerAccess dockerAccess, DockerAssemblyManager dockerAssemblyManager, Logger logger, Exec exec) {
        this.dockerAccess = dockerAccess;
        this.dockerAssemblyManager = dockerAssemblyManager;
        this.logger = logger;
        this.exec = exec;
    }

    public void build(ProjectPaths projectPaths, ImageConfiguration imageConfig, String  configuredRegistry, AuthConfigList authConfig, File buildArchive) throws MojoExecutionException {
        useBuilder(projectPaths, imageConfig,  configuredRegistry, authConfig, buildArchive, this::buildAndLoadSinglePlatform);
    }

    public void push(ProjectPaths projectPaths, ImageConfiguration imageConfig, String configuredRegistry, AuthConfigList authConfig) throws MojoExecutionException {
        BuildDirs buildDirs = new BuildDirs(projectPaths, imageConfig.getName());
        File archive = new File(buildDirs.getTemporaryRootDirectory(), "docker-build.tar");
        useBuilder(projectPaths, imageConfig, configuredRegistry, authConfig, archive, this::pushMultiPlatform);
    }

    protected <C> void useBuilder(ProjectPaths projectPaths, ImageConfiguration imageConfig, String configuredRegistry, AuthConfigList authConfig, C context, Builder<C> builder) throws MojoExecutionException {
        BuildDirs buildDirs = new BuildDirs(projectPaths, imageConfig.getName());

        Path configPath = getDockerStateDir(imageConfig.getBuildConfiguration(),  buildDirs);
        List<String> buildX = new ArrayList<>();
        buildX.add(DOCKER);
        if (isDockerCLINotLegacy() || shouldAddConfigInLegacyDockerCLI(authConfig, configuredRegistry)) {
            buildX.add("--config");
            buildX.add(configPath.toString());
        }
        buildX.add("buildx");

        String builderName = createBuilder(configPath, buildX, imageConfig, buildDirs);
        Path configJson = configPath.resolve("config.json");
        try {
            createConfigJson(configJson, authConfig);
            builder.useBuilder(buildX, builderName, buildDirs, imageConfig,  configuredRegistry, context);
        } finally {
            removeConfigJson(configJson);
        }
    }

    private boolean shouldAddConfigInLegacyDockerCLI(AuthConfigList authConfigList, String configuredRegistry) throws MojoExecutionException {
        return authConfigList != null && !authConfigList.isEmpty() &&
            !hasAuthForRegistryInDockerConfig(logger, configuredRegistry, authConfigList);
    }

    private boolean isDockerCLINotLegacy() {
        DockerVersionExternalCommand dockerVersionExternalCommand = new DockerVersionExternalCommand(logger);
        try {
            dockerVersionExternalCommand.execute();
        } catch (IOException e) {
            logger.info("Failure in getting docker CLI version", e);
        }
        String version = dockerVersionExternalCommand.getVersion();
        if (StringUtils.isNotBlank(version)) {
            version = version.replaceAll("(^')|('$)", "");
            String[] versionParts = version.split("\\.");
            logger.info("Using Docker CLI " + version);
            if (versionParts.length >= 3) {
                int cliMajorVersion = Integer.parseInt(versionParts[0]);
                return cliMajorVersion >= DOCKER_CLI_BUILDX_CONFIG_COMPATIBLE_MAJOR_VERSION;
            }
        }
        return false;
    }

    protected void createConfigJson(Path configJson, AuthConfigList authConfig) throws MojoExecutionException {
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(configJson, StandardCharsets.UTF_8,
            StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        ) {
            bufferedWriter.write(authConfig != null ? authConfig.toJson() : "{}");
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to create config.json", e);
        }
    }

    protected void removeConfigJson(Path configJson) {
        try {
            Files.deleteIfExists(configJson);
        } catch (IOException e) {
            logger.warn("Unable to delete %s", configJson);
        }
    }

    protected void buildAndLoadSinglePlatform(List<String> buildX, String builderName, BuildDirs buildDirs, ImageConfiguration imageConfig, String configuredRegistry, File buildArchive) throws MojoExecutionException {
        List<String> platforms = imageConfig.getBuildConfiguration().getBuildX().getPlatforms();
        // build and load the single-platform image by re-building, image should be cached and build should be quick
        String nativePlatform = dockerAccess.getNativePlatform();
        if (platforms.size() == 1) {
            buildX(buildX, builderName, buildDirs, imageConfig,  configuredRegistry, platforms, buildArchive, "--load");
        } else if (platforms.contains(nativePlatform)) {
            buildX(buildX, builderName, buildDirs, imageConfig,  configuredRegistry, Collections.singletonList(nativePlatform), buildArchive, "--load");
        } else  {
            logger.info("More than one platform specified not including native %s, no image built", nativePlatform);
        }
    }

    protected void pushMultiPlatform(List<String> buildX, String builderName, BuildDirs buildDirs, ImageConfiguration imageConfig, String configuredRegistry, File buildArchive) throws MojoExecutionException {
        // build and push all images.  The native platform may be re-built, image should be cached and build should be quick
        buildX(buildX, builderName, buildDirs, imageConfig, configuredRegistry, imageConfig.getBuildConfiguration().getBuildX().getPlatforms(), buildArchive, "--push");
    }

    protected void buildX(List<String> buildX, String builderName, BuildDirs buildDirs, ImageConfiguration imageConfig, String  configuredRegistry, List<String> platforms, File buildArchive, String extraParam)
        throws MojoExecutionException {

        BuildImageConfiguration buildConfiguration = imageConfig.getBuildConfiguration();

        List<String> cmdLine = new ArrayList<>(buildX);
        append(cmdLine, "build", "--progress=plain", "--builder", builderName, "--platform",
            String.join(",", platforms), "--tag",
            new ImageName(imageConfig.getName()).getFullName(configuredRegistry));
        buildConfiguration.getTags().forEach(t -> {
            cmdLine.add("--tag");
            cmdLine.add(new ImageName(imageConfig.getName(), t).getFullName(configuredRegistry));
        });

        Map<String, String> args = buildConfiguration.getArgs();
        if (args != null) {
            args.forEach((key, value) -> {
                cmdLine.add("--build-arg");
                cmdLine.add(key + '=' + value);
            });
        }
        if (ConfigHelper.isNoCache(imageConfig)) {
            cmdLine.add("--no-cache");
        }

        AttestationConfiguration attestations = buildConfiguration.getBuildX().getAttestations();
        if (attestations != null) {
            if (Boolean.TRUE.equals(attestations.getSbom())) {
                cmdLine.add("--sbom=true");
            }
            String provenance = attestations.getProvenance();
            if (provenance != null) {
                switch (provenance) {
                    case "min":
                    case "max":
                        cmdLine.add("--provenance=mode=" + provenance);
                        break;
                    case "false":
                    case "true":
                        cmdLine.add("--provenance=" + provenance);
                        break;
                    default:
                        logger.error("Unsupported provenance mode %s", provenance);
                }
            }
        }

        if (buildConfiguration.squash()) {
            cmdLine.add("--squash");
        }

        File contextDir = buildConfiguration.getContextDir();
        if (contextDir != null) {
            Path destinationPath = getContextPath(buildArchive);
            String dockerFileName = buildConfiguration.getDockerFile().getName();
            append(cmdLine, "--file=" + destinationPath.resolve(dockerFileName), destinationPath.toString());
        } else {
            cmdLine.add(buildDirs.getOutputDirectory().getAbsolutePath());
        }
        if (extraParam != null) {
            cmdLine.add(extraParam);
        }

        int rc = exec.process(cmdLine);
        if (rc != 0) {
            throw new MojoExecutionException("Error status (" + rc + ") when building");
        }
    }

    protected Path getContextPath(File buildArchive) throws MojoExecutionException {
        String archiveName = buildArchive.getName();
        String fileName = archiveName.substring(0, archiveName.indexOf('.'));
        File destinationDirectory = new File(buildArchive.getParentFile(), fileName);
        Path destinationPath = destinationDirectory.toPath();
        try {
            Files.createDirectories(destinationPath);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        dockerAssemblyManager.extractDockerTarArchive(buildArchive, destinationDirectory);
        return destinationPath;
    }

    protected Path getDockerStateDir(BuildImageConfiguration buildConfiguration, BuildDirs buildDirs) {
        String stateDir = buildConfiguration.getBuildX().getDockerStateDir();
        Path dockerStatePath = buildDirs.getBuildPath(stateDir != null ? EnvUtil.resolveHomeReference(stateDir) : DOCKER);
        createDirectory(dockerStatePath);
        return dockerStatePath;
    }

    protected void createDirectory(Path cachePath) {
        try {
            Files.createDirectories(cachePath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot create " + cachePath);
        }
    }

    protected String createBuilder(Path configPath, List<String> buildX, ImageConfiguration imageConfig, BuildDirs buildDirs) throws MojoExecutionException {
        BuildXConfiguration buildXConfiguration = imageConfig.getBuildConfiguration().getBuildX();
        String builderName = Optional.ofNullable(buildXConfiguration.getBuilderName()).orElse("maven");
        String nodeName = buildXConfiguration.getNodeName();
        Path builderPath = configPath.resolve(Paths.get("buildx", "instances", builderName));
        if(Files.notExists(builderPath)) {
            List<String> cmds = new ArrayList<>(buildX);
            append(cmds, "create", "--driver", "docker-container", "--name", builderName);
            if (nodeName != null) {
                append(cmds, "--node", nodeName);
            }
            String buildConfig = buildXConfiguration.getConfigFile();
            if(buildConfig != null) {
                append(cmds, "--config",
                buildDirs.getProjectPath(EnvUtil.resolveHomeReference(buildConfig)).toString());
            }
            int rc = exec.process(cmds);
            if (rc != 0) {
                throw new MojoExecutionException("Error status (" + rc + ") while creating builder " + builderName);
            }
        }
        return builderName;
    }

    public static <T> List<T> append(List<T> collection, T... members) {
        collection.addAll(Arrays.asList(members));
        return collection;
    }

    interface Builder<C> {
        void useBuilder(List<String> buildX, String builderName, BuildDirs buildDirs, ImageConfiguration imageConfig, String configuredRegistry, C context) throws MojoExecutionException;
    }

    public interface Exec {
        int process(List<String> cmdArgs) throws MojoExecutionException;
    }

    public static class DefaultExec implements Exec {
        private final Logger logger;

        public DefaultExec(Logger logger) {
            this.logger = logger;
        }

        @Override public int process(List<String> cmdArgs) throws MojoExecutionException {
            try {
                logger.info(String.join(" ", cmdArgs));
                ProcessBuilder builder = new ProcessBuilder(cmdArgs);
                Process process = builder.start();
                pumpStream(process.getInputStream());
                pumpStream(process.getErrorStream());
                return process.waitFor();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new MojoExecutionException("Interrupted while executing " + cmdArgs, ex);
            } catch (IOException ex) {
                throw new MojoExecutionException("unable to execute " + cmdArgs, ex);
            }
        }

        private void pumpStream(InputStream is) {
            CompletableFuture.runAsync(() -> {
                try (
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is))
                ) {
                    bufferedReader.lines().forEach(logger::info);
                } catch (IOException e) {
                    logger.error("failed redirecting stream %s", e.getMessage());
                }
            });
        }
    }

    public static class DockerVersionExternalCommand extends ExternalCommand {
        private final StringBuilder outputBuilder;
        public DockerVersionExternalCommand(Logger logger) {
            super(logger);
            outputBuilder = new StringBuilder();
        }

        @Override
        protected String[] getArgs() {
            return new String[] {DOCKER, "version", "--format", "'{{.Client.Version}}'"};
        }

        @Override
        protected void processLine(String line) {
            if (StringUtils.isNotBlank(line)) {
                outputBuilder.append(line);
            }
        }

        public String getVersion() {
            return outputBuilder.toString();
        }
    }
}
