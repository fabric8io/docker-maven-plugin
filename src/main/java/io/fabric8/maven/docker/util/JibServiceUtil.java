package io.fabric8.maven.docker.util;

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.TarImage;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.event.progress.ProgressEventHandler;
import io.fabric8.maven.docker.assembly.AssemblyFiles;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.model.Image;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.fusesource.jansi.Ansi.ansi;

public class JibServiceUtil {
    /**
     * Line above progress bar.
     */
    private static final String HEADER = "Executing tasks:";

    /**
     * Maximum number of bars in the progress display.
     */
    private static final int PROGRESS_BAR_COUNT = 30;
    public static final String JIB_LOG_PREFIX = "JIB> ";

    private JibServiceUtil() {
    }

    private static final long JIB_EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 10L;
    protected static final String BUSYBOX = "busybox:latest";

    /**
     * Build container image using JIB
     *
     * @param jibContainerBuilder jib container builder object
     * @param image tarball for image
     * @param logger kit logger
     * @throws InterruptedException in case thread is interrupted
     */
    public static void buildContainer(JibContainerBuilder jibContainerBuilder, TarImage image, Logger logger)
            throws InterruptedException {

        final ExecutorService jibBuildExecutor = Executors.newCachedThreadPool();
        try {
            jibContainerBuilder.setCreationTime(Instant.now());
            jibContainerBuilder.containerize(Containerizer.to(image)
                    .setAllowInsecureRegistries(true)
                    .setExecutorService(jibBuildExecutor)
                    .addEventHandler(LogEvent.class, log(logger))
                    .addEventHandler(ProgressEvent.class, new ProgressEventHandler(logUpdate())));
            logUpdateFinished();
        } catch (CacheDirectoryCreationException | IOException | ExecutionException | RegistryException ex) {
            logger.error("Unable to build the image tarball: ", ex);
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        } finally {
            jibBuildExecutor.shutdown();
            jibBuildExecutor.awaitTermination(JIB_EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    public static JibContainerBuilder containerFromImageConfiguration(String jibImageFormat,
                                                                      ImageConfiguration imageConfiguration, Credential pullRegistryCredential) throws InvalidImageReferenceException {
        final JibContainerBuilder containerBuilder = Jib.from(getRegistryImage(getBaseImage(imageConfiguration), pullRegistryCredential))
                .setFormat(getImageFormat(jibImageFormat));
        return populateContainerBuilderFromImageConfiguration(containerBuilder, imageConfiguration);
    }

    public static String getFullImageName(ImageConfiguration imageConfiguration, String tag) {
        ImageName imageName;
        if (tag != null) {
            imageName = new ImageName(imageConfiguration.getName(), tag);
        } else {
            imageName = new ImageName(imageConfiguration.getName());
        }
        return imageName.getFullName();
    }

    /**
     * Push Image to registry using JIB
     *
     * @param imageConfiguration ImageConfiguration
     * @param pushCredentials    push credentials
     * @param tarArchive         tar archive built during build goal
     * @param log                Logger
     */
    public static void jibPush(ImageConfiguration imageConfiguration, Credential pushCredentials, File tarArchive, boolean skipTag, Logger log) {
        BuildImageConfiguration buildConfig = imageConfiguration.getBuildConfiguration();
        try {
            if (!skipTag && !buildConfig.getTags().isEmpty()) {
                for (String tag : buildConfig.getTags()) {
                    String imageNameWithTag = getFullImageName(imageConfiguration, tag);
                    log.info("Pushing image: %s", imageNameWithTag);
                    pushImage(TarImage.at(tarArchive.toPath()), imageNameWithTag, pushCredentials, log);
                }
            } else {
                String imageName = getFullImageName(imageConfiguration, null);
                pushImage(TarImage.at(tarArchive.toPath()), imageName, pushCredentials, log);
            }
        } catch (IllegalStateException e) {
            log.error("Exception occurred while pushing the image: %s", imageConfiguration.getName());
            throw new IllegalStateException(e.getMessage(), e);
        } catch (InterruptedException e) {
            log.error("Thread interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    private static void pushImage(TarImage baseImage, String targetImageName, Credential credential, Logger logger)
            throws InterruptedException {

        final ExecutorService jibBuildExecutor = Executors.newCachedThreadPool();
        try {
            submitPushToJib(baseImage, getRegistryImage(targetImageName, credential), jibBuildExecutor, logger);
        } catch (RegistryException | CacheDirectoryCreationException | InvalidImageReferenceException | IOException | ExecutionException e) {
            logger.error("Exception occurred while pushing the image: %s, %s", targetImageName, e.getMessage());
            throw new IllegalStateException(e.getMessage(), e);
        } catch (InterruptedException ex) {
            logger.error("Thread interrupted", ex);
            throw ex;
        } finally {
            jibBuildExecutor.shutdown();
            jibBuildExecutor.awaitTermination(JIB_EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static JibContainerBuilder populateContainerBuilderFromImageConfiguration(JibContainerBuilder containerBuilder, ImageConfiguration imageConfiguration) {
        final Optional<BuildImageConfiguration> bic =
                Optional.ofNullable(Objects.requireNonNull(imageConfiguration).getBuildConfiguration());
        bic.map(BuildImageConfiguration::getEntryPoint)
                .map(Arguments::asStrings)
                .ifPresent(containerBuilder::setEntrypoint);
        bic.map(BuildImageConfiguration::getEnv)
                .ifPresent(containerBuilder::setEnvironment);
        bic.map(BuildImageConfiguration::getPorts).map(List::stream)
                .map(s -> s.map(Integer::parseInt).map(Port::tcp))
                .map(s -> s.collect(Collectors.toSet()))
                .ifPresent(containerBuilder::setExposedPorts);
        bic.map(BuildImageConfiguration::getLabels)
                .map(Map::entrySet)
                .ifPresent(labels -> labels.forEach(l -> {
                    if (l.getKey() != null && l.getValue() != null) {
                        containerBuilder.addLabel(l.getKey(), l.getValue());
                    }
                }));
        bic.map(BuildImageConfiguration::getCmd)
                .map(Arguments::asStrings)
                .ifPresent(containerBuilder::setProgramArguments);
        bic.map(BuildImageConfiguration::getUser)
                .ifPresent(containerBuilder::setUser);
        bic.map(BuildImageConfiguration::getVolumes).map(List::stream)
                .map(s -> s.map(AbsoluteUnixPath::get))
                .map(s -> s.collect(Collectors.toSet()))
                .ifPresent(containerBuilder::setVolumes);
        bic.map(BuildImageConfiguration::getWorkdir)
                .filter(((Predicate<String>) String::isEmpty).negate())
                .map(AbsoluteUnixPath::get)
                .ifPresent(containerBuilder::setWorkingDirectory);
        return containerBuilder;
    }

    static ImageFormat getImageFormat(String jibImageFormat) {
        if (jibImageFormat != null && jibImageFormat.toLowerCase().equalsIgnoreCase("oci")) {
            return ImageFormat.OCI;
        }
        return ImageFormat.Docker;
    }

    private static void submitPushToJib(TarImage baseImage, RegistryImage targetImage, ExecutorService jibBuildExecutor, Logger logger) throws InterruptedException, ExecutionException, RegistryException, CacheDirectoryCreationException, IOException {
        Jib.from(baseImage).setCreationTime(Instant.now()).containerize(Containerizer.to(targetImage)
                .setAllowInsecureRegistries(true)
                .setExecutorService(jibBuildExecutor)
                .addEventHandler(LogEvent.class, log(logger))
                .addEventHandler(ProgressEvent.class, new ProgressEventHandler(logUpdate())));
        logUpdateFinished();
    }

    private static RegistryImage getRegistryImage(String targetImage, Credential credential) throws InvalidImageReferenceException {
        RegistryImage registryImage = RegistryImage.named(targetImage);
        if (credential != null && !credential.getUsername().isEmpty() && !credential.getPassword().isEmpty()) {
            registryImage.addCredential(credential.getUsername(), credential.getPassword());
        }
        return registryImage;
    }

    private static Consumer<LogEvent> log(Logger logger) {
        return le -> {
            if (le.getLevel() != LogEvent.Level.DEBUG || logger.isVerboseEnabled() || logger.isDebugEnabled()) {
                System.out.println(ansi().cursorUpLine(1).eraseLine().a(JIB_LOG_PREFIX)
                        .a(StringUtils.rightPad(le.getMessage(), 120)).a("\n"));
            }
        };
    }

    private static Consumer<ProgressEventHandler.Update> logUpdate() {
        return update -> {
            final List<String> progressDisplay =
                    generateProgressDisplay(update.getProgress(), update.getUnfinishedLeafTasks());
            if (progressDisplay.size() > 2 && progressDisplay.stream().allMatch(Objects::nonNull)) {
                final String progressBar = progressDisplay.get(1);
                final String task = progressDisplay.get(2);
                System.out.println(ansi().cursorUpLine(1).eraseLine().a(JIB_LOG_PREFIX).a(progressBar).a(" ").a(task));
            }
        };
    }

    private static void logUpdateFinished() {
        System.out.println(JIB_LOG_PREFIX + generateProgressBar(1.0F));
    }

    public static String getBaseImage(ImageConfiguration imageConfiguration) {
        return Optional.ofNullable(imageConfiguration)
                .map(ImageConfiguration::getBuildConfiguration)
                .map(BuildImageConfiguration::getFrom)
                .filter(((Predicate<String>) String::isEmpty).negate())
                .orElse(BUSYBOX);
    }

    public static void copyToContainer(
            JibContainerBuilder containerBuilder, File directory, String targetDir, Map<File, AssemblyFiles.Entry> files)
            throws IOException {

        Files.walkFileTree(directory.toPath(), new FileVisitor<Path>() {
            boolean notParentDir = false;

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {

                if (!notParentDir) {
                    notParentDir = true;
                    return FileVisitResult.CONTINUE;
                }

                String fileFullpath = dir.toAbsolutePath().toString();
                String relativePath = fileFullpath.substring(targetDir.length());
                AbsoluteUnixPath absoluteUnixPath = AbsoluteUnixPath.fromPath(Paths.get(relativePath));
                containerBuilder.addFileEntriesLayer(FileEntriesLayer.builder()
                        .addEntryRecursive(dir, absoluteUnixPath)
                        .build());
                return FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileFullpath = file.toAbsolutePath().toString();
                String relativePath = fileFullpath.substring(targetDir.length());
                AbsoluteUnixPath absoluteUnixPath = AbsoluteUnixPath.fromPath(Paths.get(relativePath));
                containerBuilder.addFileEntriesLayer(FileEntriesLayer.builder()
                        .addEntryRecursive(file, absoluteUnixPath/*, filePermissionsProvider*/)
                        .build());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                if (exc != null) {
                    throw new IOException(exc);
                }
                return FileVisitResult.TERMINATE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw new IOException(exc);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Generates a progress display.
     *
     * Taken from https://github.com/GoogleContainerTools/jib/blob/master/jib-plugins-common/src/main/java/com/google/cloud/tools/jib/plugins/common/logging/ProgressDisplayGenerator.java#L47
     *
     * @param progress the overall progress, with {@code 1.0} meaning fully complete
     * @param unfinishedLeafTasks the unfinished leaf tasks
     * @return the progress display as a list of lines
     */
    private static List<String> generateProgressDisplay(double progress, List<String> unfinishedLeafTasks) {
        List<String> lines = new ArrayList<>();

        lines.add(HEADER);
        lines.add(generateProgressBar(progress));
        for (String task : unfinishedLeafTasks) {
            lines.add("> " + task);
        }

        return lines;
    }

    /**
     * Generates the progress bar line.
     *
     * Taken from https://github.com/GoogleContainerTools/jib/blob/master/jib-plugins-common/src/main/java/com/google/cloud/tools/jib/plugins/common/logging/ProgressDisplayGenerator.java#L66
     *
     * @param progress the overall progress, with {@code 1.0} meaning fully complete
     * @return the progress bar line
     */
    private static String generateProgressBar(double progress) {
        StringBuilder progressBar = new StringBuilder();
        progressBar.append('[');

        int barsToDisplay = (int) Math.round(PROGRESS_BAR_COUNT * progress);
        for (int barIndex = 0; barIndex < PROGRESS_BAR_COUNT; barIndex++) {
            progressBar.append(barIndex < barsToDisplay ? '=' : ' ');
        }

        return progressBar
                .append(']')
                .append(String.format(" %.1f", progress * 100))
                .append("% complete")
                .toString();
    }
}
