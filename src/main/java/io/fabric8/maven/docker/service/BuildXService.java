package io.fabric8.maven.docker.service;

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
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.maven.plugin.MojoExecutionException;

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
import io.fabric8.maven.docker.config.SecretConfiguration;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.ProjectPaths;

public class BuildXService {
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
        List<String> buildX = new ArrayList<>(Arrays.asList(DOCKER, "--config", configPath.toString(), "buildx"));
        if (!isDockerBuildXWorkingWithOverriddenConfig(configPath)) {
            logger.debug("Detected current version of BuildX not working with --config override");
            copyBuildXToConfigPathIfBuildXBinaryInDefaultDockerConfig(configPath);
        }

        String builderName = createBuilder(configPath, buildX, imageConfig, buildDirs);
        Path configJson = configPath.resolve("config.json");
        try {
            createConfigJson(configJson, authConfig);
            builder.useBuilder(buildX, builderName, buildDirs, imageConfig,  configuredRegistry, context);
        } finally {
            removeConfigJson(configJson);
        }
    }

    private void copyBuildXToConfigPathIfBuildXBinaryInDefaultDockerConfig(Path configPath) {
        try {
            String dockerBuildxExecutableName = "docker-buildx" + (EnvUtil.isWindows()?".exe":"");
            File buildXInUserHomeDockerConfig = Paths.get(EnvUtil.getUserHome(), ".docker/cli-plugins/" + dockerBuildxExecutableName).toFile();
            Files.createDirectory(configPath.resolve("cli-plugins"));
            if (buildXInUserHomeDockerConfig.exists() && buildXInUserHomeDockerConfig.isFile()) {
                Files.copy(buildXInUserHomeDockerConfig.toPath(), configPath.resolve("cli-plugins").resolve(dockerBuildxExecutableName), StandardCopyOption.COPY_ATTRIBUTES);
                logger.debug("Copying BuildX binary to " + configPath);
            }
        } catch (IOException exception) {
            logger.debug(exception.getMessage());
        }
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
        } else if (platforms.isEmpty() || platforms.contains(nativePlatform)) {
            buildX(buildX, builderName, buildDirs, imageConfig,  configuredRegistry, Collections.singletonList(nativePlatform), buildArchive, "--load");
        } else  {
            logger.info("More than one platform specified not including native %s, no image built", nativePlatform);
        }
    }

    protected void pushMultiPlatform(List<String> buildX, String builderName, BuildDirs buildDirs, ImageConfiguration imageConfig, String configuredRegistry, File buildArchive) throws MojoExecutionException {
        // build and push all images.  The native platform may be re-built, image should be cached and build should be quick
        List<String> platforms = new ArrayList<>(imageConfig.getBuildConfiguration().getBuildX().getPlatforms());
        if (platforms.isEmpty()) {
            platforms.add(dockerAccess.getNativePlatform());
        }
        buildX(buildX, builderName, buildDirs, imageConfig, configuredRegistry, platforms, buildArchive, "--push");
    }

    protected void buildX(List<String> buildX, String builderName, BuildDirs buildDirs, ImageConfiguration imageConfig, String  configuredRegistry, List<String> platforms, File buildArchive, String extraParam)
        throws MojoExecutionException {

        BuildImageConfiguration buildConfiguration = imageConfig.getBuildConfiguration();

        List<String> cmdLine = new ArrayList<>(buildX);
        append(cmdLine, "build", "--progress=plain", "--builder", builderName, "--platform",
            String.join(",", platforms), "--tag",
            new ImageName(imageConfig.getName()).getFullName(configuredRegistry));
        if (!buildConfiguration.skipTag()) {
            buildConfiguration.getTags().forEach(t -> {
                cmdLine.add("--tag");
                cmdLine.add(new ImageName(imageConfig.getName(), t).getFullName(configuredRegistry));
            });
        }

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

        String networkMode = ConfigHelper.getNetwork(imageConfig);
        if (networkMode!=null) {
            cmdLine.add("--network="+networkMode);
        }

        BuildXConfiguration buildXConfiguration = buildConfiguration.getBuildX();
        AttestationConfiguration attestations = buildXConfiguration.getAttestations();
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

        if (buildXConfiguration.getCacheFrom() != null) {
            cmdLine.add("--cache-from=" + buildXConfiguration.getCacheFrom());
        }

        if (buildXConfiguration.getCacheTo() != null) {
            cmdLine.add("--cache-to=" + buildXConfiguration.getCacheTo());
        }
        SecretConfiguration secret = buildXConfiguration.getSecret();
        if (secret != null) {
            if (secret.getEnvs() != null) {
                secret.getEnvs().forEach(buildXSecretConsumerFor("env", cmdLine::add));
            }
            if (secret.getFiles() != null) {
                secret.getFiles().forEach(buildXSecretConsumerFor("src", cmdLine::add));
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

    protected BiConsumer<String, String> buildXSecretConsumerFor(String attribute, Consumer<String> cmdLineConsumer) {
        return (arg0, arg1) -> {
            cmdLineConsumer.accept("--secret");
            String secretParameter = "id=" + arg0;
            if (arg1 != null) {
                secretParameter += "," + attribute + "=" + arg1;
            }
            cmdLineConsumer.accept(secretParameter);
        };
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

        if ("default".equals(builderName)) {
            logger.info("Using default builder with buildx - only single platforms will be supported");
        } else {
            createCustomBuilderIfNotExists(configPath, buildX, buildXConfiguration, builderName, buildDirs);
        }
        return builderName;
    }

    private void createCustomBuilderIfNotExists(Path configPath, List<String> buildX, BuildXConfiguration buildXConfiguration, String builderName, BuildDirs buildDirs) throws MojoExecutionException {
        String nodeName = buildXConfiguration.getNodeName();
        Path builderPath = configPath.resolve(Paths.get("buildx", "instances", builderName.toLowerCase()));

        if (Files.notExists(builderPath)) {
            List<String> cmds = new ArrayList<>(buildX);
            append(cmds, "create", "--driver", "docker-container", "--name", builderName);

            if (nodeName != null) {
                append(cmds, "--node", nodeName);
            }

            addDriverOptions(cmds, buildXConfiguration);
            addBuildConfig(cmds, buildXConfiguration, buildDirs);

            int rc = exec.process(cmds);
            if (rc != 0) {
                throw new MojoExecutionException("Error status (" + rc + ") while creating builder " + builderName);
            }
        }
    }

    private void addDriverOptions(List<String> cmds, BuildXConfiguration buildXConfiguration) {
        if (buildXConfiguration.getDriverOpts() != null && !buildXConfiguration.getDriverOpts().isEmpty()) {
            buildXConfiguration.getDriverOpts().forEach((key, value) -> append(cmds, "--driver-opt", key + '=' + value));
        }
    }

    private void addBuildConfig(List<String> cmds, BuildXConfiguration buildXConfiguration, BuildDirs buildDirs) {
        String buildConfig = buildXConfiguration.getConfigFile();
        if (buildConfig != null) {
            append(cmds, "--config",
                    buildDirs.getProjectPath(EnvUtil.resolveHomeReference(buildConfig)).toString());
        }
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

    private boolean isDockerBuildXWorkingWithOverriddenConfig(Path configPath) {
        BuildXListWithConfigCommand buildXList = new BuildXListWithConfigCommand(logger, configPath);
        try {
            buildXList.execute();
            return buildXList.isSuccessFul();
        } catch (IOException e) {
          return false;
        }
    }

    static class BuildXListWithConfigCommand extends ExternalCommand {
        private final Path configPath;
        public BuildXListWithConfigCommand(Logger logger, Path configPath) {
            super(logger);
            this.configPath = configPath;
        }

        @Override
        protected String[] getArgs() {
            return new String[] { DOCKER, "--config", configPath.toString(), "buildx", "ls"};
        }

        public boolean isSuccessFul() {
            return getStatusCode() == 0;
        }
    }
}
