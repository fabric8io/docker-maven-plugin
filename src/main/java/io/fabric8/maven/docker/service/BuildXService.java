package io.fabric8.maven.docker.service;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.assembly.BuildDirs;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.BuildXConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.EnvUtil;
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
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BuildXService {
    private final DockerAccess dockerAccess;
    private final Logger logger;
    private final Exec exec;

    public BuildXService(DockerAccess dockerAccess, Logger logger) {
        this(dockerAccess, logger, new DefaultExec(logger));
    }

    public BuildXService(DockerAccess dockerAccess, Logger logger, Exec exec) {
        this.dockerAccess = dockerAccess;
        this.logger = logger;
        this.exec = exec;
    }

    public void build(ProjectPaths projectPaths, ImageConfiguration imageConfig, AuthConfig authConfig) throws MojoExecutionException {
        createAndRemoveBuilder(projectPaths, imageConfig, authConfig, this::buildMultiPlatform);
    }

    public void push(ProjectPaths projectPaths, ImageConfiguration imageConfig, AuthConfig authConfig) throws MojoExecutionException {
        createAndRemoveBuilder(projectPaths, imageConfig, authConfig, this::pushMultiPlatform);
    }

    private void createAndRemoveBuilder(ProjectPaths projectPaths, ImageConfiguration imageConfig, AuthConfig authConfig, Builder builder) throws MojoExecutionException {
        BuildDirs buildDirs = new BuildDirs(projectPaths, imageConfig.getName());

        Path configPath = buildDirs.getBuildPath("docker");
        createDirectory(configPath);
        List<String> buildX = Arrays.asList("docker", "--config", configPath.toString(), "buildx");

        Map.Entry<String, Boolean> builderName = createBuilder(buildX, imageConfig, buildDirs);
        try {
            Path configJson = configPath.resolve("config.json");
            try {
                createConfigJson(configJson, authConfig);
                builder.useBuilder(buildX, builderName.getKey(), buildDirs, imageConfig);
            } finally {
                removeConfigJson(configJson);
            }
        } finally {
            removeBuilder(buildX, builderName);
        }
    }

    private void createConfigJson(Path configJson, AuthConfig authConfig) throws MojoExecutionException {
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(configJson, StandardCharsets.UTF_8,
            StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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

    private void buildMultiPlatform(List<String> buildX, String builderName, BuildDirs buildDirs, ImageConfiguration imageConfig) throws MojoExecutionException {
        buildX(buildX, builderName, buildDirs, imageConfig, imageConfig.getBuildConfiguration().getBuildX().getPlatforms(), null);
        buildX(buildX, builderName, buildDirs, imageConfig, Collections.singletonList(dockerAccess.getNativePlatform()), "--load");
    }

    private void pushMultiPlatform(List<String> buildX, String builderName, BuildDirs buildDirs, ImageConfiguration imageConfig) throws MojoExecutionException {
        buildX(buildX, builderName, buildDirs, imageConfig, imageConfig.getBuildConfiguration().getBuildX().getPlatforms(), "--push");
    }

    private void buildX(List<String> buildX, String builderName, BuildDirs buildDirs, ImageConfiguration imageConfig, List<String> platforms, String extraParam)
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
                cmdLine.add(t);
            }
        );
        cmdLine.add("--tag");
        cmdLine.add(imageConfig.getName());

        Map<String, String> args = buildConfiguration.getArgs();
        if (args != null) {
            args.forEach((key, value) -> {
                cmdLine.add("--build-arg");
                cmdLine.add(key + '=' + value);
            });
        }
        if (extraParam != null) {
            cmdLine.add(extraParam);
        }

        String cacheDir = getCacheDir(buildConfiguration, buildDirs);
        cmdLine.add("--cache-to=type=local,dest=" + cacheDir);
        cmdLine.add("--cache-from=type=local,src=" + cacheDir);

        String dockerfileName = buildConfiguration.getDockerfileName();
        if (dockerfileName != null) {
            cmdLine.add("--file");
            cmdLine.add(dockerfileName);
        }
        cmdLine.add(buildDirs.getOutputDirectory().getAbsolutePath());

        int rc = exec.process(cmdLine);
        if (rc != 0) {
            throw new MojoExecutionException("Error status (" + rc + ") when building");
        }
    }

    private String getCacheDir(BuildImageConfiguration buildConfiguration, BuildDirs buildDirs) {
        String cache = buildConfiguration.getBuildX().getCache();
        Path cachePath = buildDirs.getBuildPath(cache != null ? EnvUtil.resolveHomeReference(cache) : "cache");
        createDirectory(cachePath);
        return cachePath.toString();
    }

    private void createDirectory(Path cachePath) {
        try {
            Files.createDirectories(cachePath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot create " + cachePath);
        }
    }

    private Map.Entry<String, Boolean> createBuilder(List<String> buildX, ImageConfiguration imageConfig, BuildDirs buildDirs) throws MojoExecutionException {
        BuildXConfiguration buildXConfiguration = imageConfig.getBuildConfiguration().getBuildX();
        String configuredBuilder = buildXConfiguration.getBuilderName();
        if (configuredBuilder != null) {
            return new AbstractMap.SimpleEntry<>(configuredBuilder, false);
        }

        String builderName = "dmp_" + buildDirs.getBuildTopDir().replace(File.separatorChar, '_');
        String buildConfig = buildXConfiguration.getConfigFile();
        int rc = exec.process(buildX, buildConfig == null
            ? new String[] { "create", "--driver", "docker-container", "--name", builderName }
            : new String[] { "create", "--driver", "docker-container", "--name", builderName, "--config",
                buildDirs.getProjectPath(EnvUtil.resolveHomeReference(buildConfig)).toString() }
        );
        if (rc != 0) {
            throw new MojoExecutionException("Error status (" + rc + ") while creating builder " + builderName);
        }
        return new AbstractMap.SimpleEntry<>(builderName, true);
    }

    private void removeBuilder(List<String> buildX, Map.Entry<String, Boolean> builderName) throws MojoExecutionException {
        if (Boolean.TRUE.equals(builderName.getValue())) {
            int rc = exec.process(buildX, "rm", builderName.getKey());
            if (rc != 0) {
                logger.warn("Ignoring non-zero status (" + rc + ") while removing builder " + builderName);
            }
        }
    }

    interface Builder {
        void useBuilder(List<String> buildX, String builderName, BuildDirs buildDirs, ImageConfiguration imageConfig) throws MojoExecutionException;
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
