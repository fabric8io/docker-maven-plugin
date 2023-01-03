package io.fabric8.maven.docker.service;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.assembly.BuildDirs;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.BuildXConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.ProjectPaths;
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
import java.util.concurrent.CompletableFuture;

public class BuildXService {
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

    public void build(ProjectPaths projectPaths, ImageConfiguration imageConfig, String  configuredRegistry, AuthConfig authConfig, File buildArchive) throws MojoExecutionException {
        useBuilder(projectPaths, imageConfig,  configuredRegistry, authConfig, buildArchive, this::buildAndLoadNativePlatform);
    }

    public void push(ProjectPaths projectPaths, ImageConfiguration imageConfig, String configuredRegistry, AuthConfig authConfig) throws MojoExecutionException {
        BuildDirs buildDirs = new BuildDirs(projectPaths, imageConfig.getName());
        File archive = new File(buildDirs.getTemporaryRootDirectory(), "docker-build.tar");
        useBuilder(projectPaths, imageConfig, configuredRegistry, authConfig, archive, this::pushMultiPlatform);
    }

    private <C> void useBuilder(ProjectPaths projectPaths, ImageConfiguration imageConfig, String configuredRegistry, AuthConfig authConfig, C context, Builder<C> builder) throws MojoExecutionException {
        BuildDirs buildDirs = new BuildDirs(projectPaths, imageConfig.getName());

        Path configPath = getDockerStateDir(imageConfig.getBuildConfiguration(),  buildDirs);
        List<String> buildX = Arrays.asList("docker", "--config", configPath.toString(), "buildx");

        String builderName = createBuilder(configPath, buildX, imageConfig, buildDirs);
        Path configJson = configPath.resolve("config.json");
        try {
            createConfigJson(configJson, authConfig);
            builder.useBuilder(buildX, builderName, buildDirs, imageConfig,  configuredRegistry, context);
        } finally {
            removeConfigJson(configJson);
        }
    }

    private void createConfigJson(Path configJson, AuthConfig authConfig) throws MojoExecutionException {
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(configJson, StandardCharsets.UTF_8,
            StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        ) {
            bufferedWriter.write(authConfig != null ? authConfig.toJson() : "{}");
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to create config.json", e);
        }
    }

    private void removeConfigJson(Path configJson) {
        try {
            Files.deleteIfExists(configJson);
        } catch (IOException e) {
            logger.warn("Unable to delete %s", configJson);
        }
    }

    private void buildAndLoadNativePlatform(List<String> buildX, String builderName, BuildDirs buildDirs, ImageConfiguration imageConfig, String configuredRegistry, File buildArchive) throws MojoExecutionException {
        List<String> platforms = imageConfig.getBuildConfiguration().getBuildX().getPlatforms();
        // build and load the native image by re-building, image should be cached and build should be quick
        String nativePlatform = dockerAccess.getNativePlatform();
        if (platforms.contains(nativePlatform)) {
            buildX(buildX, builderName, buildDirs, imageConfig,  configuredRegistry, Collections.singletonList(nativePlatform), buildArchive, "--load");
        } else  {
            logger.info("Native platform not specified, no image built");
        }
    }

    private void pushMultiPlatform(List<String> buildX, String builderName, BuildDirs buildDirs, ImageConfiguration imageConfig, String configuredRegistry, File buildArchive) throws MojoExecutionException {
        // build and push all images.  The native platform may be re-built, image should be cached and build should be quick
        buildX(buildX, builderName, buildDirs, imageConfig, configuredRegistry, imageConfig.getBuildConfiguration().getBuildX().getPlatforms(), buildArchive, "--push");
    }

    private void buildX(List<String> buildX, String builderName, BuildDirs buildDirs, ImageConfiguration imageConfig, String  configuredRegistry, List<String> platforms, File buildArchive, String extraParam)
        throws MojoExecutionException {

        BuildImageConfiguration buildConfiguration = imageConfig.getBuildConfiguration();

        List<String> cmdLine = new ArrayList<>(buildX);
        cmdLine.add("build");
        cmdLine.add("--progress=plain");
        cmdLine.add("--builder");
        cmdLine.add(builderName);
        cmdLine.add("--platform");
        cmdLine.add(String.join(",", platforms));
        buildConfiguration.getTags().forEach(t -> {
                cmdLine.add("--tag");
                cmdLine.add(new ImageName(imageConfig.getName(), t).getFullName(configuredRegistry));
            }
        );
        cmdLine.add("--tag");
        cmdLine.add(new ImageName(imageConfig.getName()).getFullName(configuredRegistry));

        Map<String, String> args = buildConfiguration.getArgs();
        if (args != null) {
            args.forEach((key, value) -> {
                cmdLine.add("--build-arg");
                cmdLine.add(key + '=' + value);
            });
        }
        if (buildConfiguration.squash()) {
            cmdLine.add("--squash");
        }
        if (extraParam != null) {
            cmdLine.add(extraParam);
        }

        String[] ctxCmds;
        File contextDir = buildConfiguration.getContextDir();
        if (contextDir != null) {
            Path destinationPath = getContextPath(buildArchive);
            Path dockerFileRelativePath = contextDir.toPath().relativize(buildConfiguration.getDockerFile().toPath());
            ctxCmds = new String[] { "--file=" + destinationPath.resolve(dockerFileRelativePath), destinationPath.toString() };
        } else {
            ctxCmds = new String[] { buildDirs.getOutputDirectory().getAbsolutePath() };
        }

        int rc = exec.process(cmdLine, ctxCmds);
        if (rc != 0) {
            throw new MojoExecutionException("Error status (" + rc + ") when building");
        }
    }

    private Path getContextPath(File buildArchive) throws MojoExecutionException {
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

    private Path getDockerStateDir(BuildImageConfiguration buildConfiguration, BuildDirs buildDirs) {
        String stateDir = buildConfiguration.getBuildX().getDockerStateDir();
        Path dockerStatePath = buildDirs.getBuildPath(stateDir != null ? EnvUtil.resolveHomeReference(stateDir) : "docker");
        createDirectory(dockerStatePath);
        return dockerStatePath;
    }

    private void createDirectory(Path cachePath) {
        try {
            Files.createDirectories(cachePath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot create " + cachePath);
        }
    }

    private String createBuilder(Path configPath, List<String> buildX, ImageConfiguration imageConfig, BuildDirs buildDirs) throws MojoExecutionException {
        BuildXConfiguration buildXConfiguration = imageConfig.getBuildConfiguration().getBuildX();
        String builderName = buildXConfiguration.getBuilderName();
        if (builderName == null) {
            builderName= "maven";
        }
        Path builderPath = configPath.resolve(Paths.get("buildx", "instances", builderName));
        if(Files.notExists(builderPath)) {
            String buildConfig = buildXConfiguration.getConfigFile();
            int rc = exec.process(buildX, buildConfig == null
                ? new String[] { "create", "--driver", "docker-container", "--name", builderName }
                : new String[] { "create", "--driver", "docker-container", "--name", builderName, "--config",
                buildDirs.getProjectPath(EnvUtil.resolveHomeReference(buildConfig)).toString() }
            );
            if (rc != 0) {
                throw new MojoExecutionException("Error status (" + rc + ") while creating builder " + builderName);
            }
        }
        return builderName;
    }

    interface Builder<C> {
        void useBuilder(List<String> buildX, String builderName, BuildDirs buildDirs, ImageConfiguration imageConfig, String configuredRegistry, C context) throws MojoExecutionException;
    }

    public interface Exec {
        int process(List<String> buildX, String... cmd) throws MojoExecutionException;
    }

    public static class DefaultExec implements Exec {
        private final Logger logger;

        public DefaultExec(Logger logger) {
            this.logger = logger;
        }

        @Override public int process(List<String> buildX, String... cmd) throws MojoExecutionException {
            List<String> cmdLine;
            if (cmd.length > 0) {
                cmdLine = new ArrayList<>(buildX);
                cmdLine.addAll(Arrays.asList(cmd));
            } else {
                cmdLine = buildX;
            }
            try {
                logger.info(String.join(" ", cmdLine));
                ProcessBuilder builder = new ProcessBuilder(cmdLine);
                Process process = builder.start();
                pumpStream(process.getInputStream());
                pumpStream(process.getErrorStream());
                return process.waitFor();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new MojoExecutionException("Interrupted while executing " + cmdLine, ex);
            } catch (IOException ex) {
                throw new MojoExecutionException("unable to execute " + cmdLine, ex);
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
}
