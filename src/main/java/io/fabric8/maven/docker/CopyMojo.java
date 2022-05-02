package io.fabric8.maven.docker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.config.CopyConfiguration;
import io.fabric8.maven.docker.config.CopyConfiguration.Entry;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.service.ArchiveService;
import io.fabric8.maven.docker.service.QueryService;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.service.RunService;
import io.fabric8.maven.docker.service.RunService.ContainerDescriptor;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.ContainerNamingUtil;
import io.fabric8.maven.docker.util.GavLabel;
import io.fabric8.maven.docker.util.Logger;

/**
 * <p>Mojo for copying file or directory from container.<p/>
 *
 * <p>When called with <code>true</code> value of <code>createContainers</code> option, then all images which are
 * configured in the project are iterated. For each image a temporary container is created (but not started) before the
 * copying and is removed after completion of the copying, even if the copying failed.<p/>
 *
 * <p>When called with <code>false</code> value of <code>createContainers</code> option (default value) and together
 * with <code>docker:start</code> goal, then only the containers started by that goal are examined. Otherwise containers
 * matching images configured in the project are searched and the copying is performed from the found containers
 * only.</p>
 */
@Mojo(name = "copy", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class CopyMojo extends AbstractDockerMojo {

    private static final String COPY_NAME_PATTERN_CONFIG = "copyNamePattern";
    private static final String TEMP_ARCHIVE_FILE_PREFIX = "docker-copy-";
    private static final String TEMP_ARCHIVE_FILE_SUFFIX = ".tar";

    /**
     * Whether to create containers or to copy from existing containers.
     */
    @Parameter(property = "docker.createContainers", defaultValue = "false")
    boolean createContainers;

    @Parameter(property = "docker.pull.registry")
    String pullRegistry;

    /**
     * Naming pattern for how to name containers when created.
     */
    @Parameter(property = "docker.containerNamePattern")
    String containerNamePattern = ContainerNamingUtil.DEFAULT_CONTAINER_NAME_PATTERN;

    /**
     * Whether to copy from all containers matching configured images or only from the newest ones.
     */
    @Parameter(property = "docker.copyAll", defaultValue = "false")
    boolean copyAll;

    @Override
    protected void executeInternal(ServiceHub hub) throws IOException, MojoExecutionException {
        DockerAccess dockerAccess = hub.getDockerAccess();
        RunService runService = hub.getRunService();
        ArchiveService archiveService = hub.getArchiveService();
        QueryService queryService = hub.getQueryService();
        GavLabel gavLabel = getGavLabel();
        if (createContainers) {
            RegistryService registryService = hub.getRegistryService();
            log.debug("Copy mojo is invoked standalone, copying from new temporary containers");
            copyFromTemporaryContainers(dockerAccess, runService, registryService, archiveService, queryService,
                    gavLabel);
        } else if (invokedTogetherWithDockerStart()) {
            log.debug("Copy mojo is invoked together with start mojo, copying from containers created by start mojo");
            copyFromStartedContainers(dockerAccess, runService, archiveService, gavLabel);
        } else {
            log.debug("Copy mojo is invoked standalone, copying from existing containers");
            copyFromExistingContainers(dockerAccess, archiveService, queryService);
        }
    }

    protected String getPullRegistry() {
        return pullRegistry;
    }

    private void copyFromTemporaryContainers(DockerAccess dockerAccess, RunService runService,
            RegistryService registryService, ArchiveService archiveService, QueryService queryService,
            GavLabel gavLabel) throws IOException, MojoExecutionException {
        List<ImageConfiguration> imageConfigurations = getResolvedImages();
        for (ImageConfiguration imageConfiguration : imageConfigurations) {
            CopyConfiguration copyConfiguration = imageConfiguration.getCopyConfiguration();
            String imageName = imageConfiguration.getName();
            if (isEmpty(copyConfiguration)) {
                log.debug("Copy configuration is not defined for %s image, skipping coping", imageName);
                continue;
            }
            try (ContainerRemover containerRemover = new ContainerRemover(log, runService, removeVolumes)) {
                String containerId = createContainer(runService, registryService, imageConfiguration, gavLabel);
                containerRemover.setContainerId(containerId);
                log.debug("Created %s container from %s image", containerId, imageName);
                copy(dockerAccess, archiveService, containerId, imageName, copyConfiguration);
            }
        }
    }

    private void copyFromStartedContainers(DockerAccess dockerAccess, RunService runService,
            ArchiveService archiveService, GavLabel gavLabel) throws IOException, MojoExecutionException {
        List<ContainerDescriptor> containerDescriptors = runService.getContainers(gavLabel);
        for (ContainerDescriptor containerDescriptor : containerDescriptors) {
            ImageConfiguration imageConfiguration = containerDescriptor.getImageConfig();
            CopyConfiguration copyConfiguration = imageConfiguration.getCopyConfiguration();
            String imageName = imageConfiguration.getName();
            if (isEmpty(copyConfiguration)) {
                log.debug("Copy configuration is not defined for %s image, skipping coping", imageName);
                continue;
            }
            String containerId = containerDescriptor.getContainerId();
            log.debug("Found %s container of %s image started by start mojo", containerId, imageName);
            copy(dockerAccess, archiveService, containerId, imageName, copyConfiguration);
        }
    }

    private void copyFromExistingContainers(DockerAccess dockerAccess, ArchiveService archiveService,
            QueryService queryService) throws IOException, MojoExecutionException {
        List<ImageConfiguration> imageConfigurations = getResolvedImages();
        for (ImageConfiguration imageConfiguration : imageConfigurations) {
            CopyConfiguration copyConfiguration = imageConfiguration.getCopyConfiguration();
            String imageName = imageConfiguration.getName();
            if (isEmpty(copyConfiguration)) {
                log.debug("Copy configuration is not defined for %s image, skipping coping", imageName);
                continue;
            }
            Collection<Container> containers = getContainersForImage(queryService, imageConfiguration);
            if (containers.isEmpty()) {
                log.warn("Found no containers of %s image", imageName);
                continue;
            }
            if (containers.size() > 1) {
                log.warn("Found more than one container of %s image", imageName);
            }
            for (Container container : containers) {
                String containerId = container.getId();
                log.debug("Found %s container of %s image", containerId, imageName);
                copy(dockerAccess, archiveService, containerId, imageName, copyConfiguration);
            }
        }
    }

    private boolean isEmpty(CopyConfiguration copyConfiguration) {
        if (copyConfiguration == null) {
            return true;
        }
        List<Entry> copyEntries = copyConfiguration.getEntries();
        return copyEntries == null || copyEntries.isEmpty();
    }

    private List<Container> getContainersForImage(QueryService queryService, ImageConfiguration imageConfiguration)
            throws IOException, MojoExecutionException {
        String imageName = imageConfiguration.getName();
        String copyNamePattern = imageConfiguration.getCopyNamePattern();
        Matcher containerNameMatcher =
                copyNamePattern == null ? null : getContainerNameMatcher(copyNamePattern, COPY_NAME_PATTERN_CONFIG);
        if (copyAll) {
            if (containerNameMatcher == null) {
                return queryService.getContainersForImage(imageName, true);
            }
            return getContainersForPattern(queryService, true, null, containerNameMatcher, COPY_NAME_PATTERN_CONFIG);
        }
        Container latestContainer;
        if (containerNameMatcher == null) {
            latestContainer = queryService.getLatestContainerForImage(imageName, true);
        } else {
            List<Container> matchingContainers = getContainersForPattern(queryService, true, null, containerNameMatcher,
                    COPY_NAME_PATTERN_CONFIG);
            latestContainer = queryService.getLatestContainer(matchingContainers);
        }
        return latestContainer == null ? Collections.emptyList() : Collections.singletonList(latestContainer);
    }

    private String createContainer(RunService runService, RegistryService registryService, ImageConfiguration imageConfiguration, GavLabel gavLabel) throws IOException, MojoExecutionException {
        Properties projectProperties = project.getProperties();
        pullImage(registryService, imageConfiguration, pullRegistry);
        return runService.createContainer(imageConfiguration,
                runService.createPortMapping(imageConfiguration.getRunConfiguration(), projectProperties), gavLabel,
                projectProperties, project.getBasedir(), containerNamePattern, getBuildTimestamp());
    }

    private void copy(DockerAccess dockerAccess, ArchiveService archiveService, String containerId, String imageName,
            CopyConfiguration copyConfiguration) throws IOException, MojoExecutionException {
        List<CopyConfiguration.Entry> copyEntries = copyConfiguration.getEntries();
        for (CopyConfiguration.Entry copyEntry : copyEntries) {
            String containerPath = copyEntry.getContainerPath();
            if (containerPath == null) {
                log.error("containerPath of copy goal entry for %s image is not specified", imageName);
                throw new IllegalArgumentException("containerPath should be specified");
            }
            File hostDirectory = getHostDirectory(copyEntry.getHostDirectory());
            log.info("Copying %s from %s container into %s host directory", containerPath, containerId,
                    hostDirectory.getAbsolutePath());
            Files.createDirectories(hostDirectory.toPath());
            try (FileRemover fileRemover = new FileRemover(log)) {
                File archiveFile = Files.createTempFile(TEMP_ARCHIVE_FILE_PREFIX, TEMP_ARCHIVE_FILE_SUFFIX).toFile();
                fileRemover.setFile(archiveFile);
                log.debug("Created %s temporary file for docker copy archive", archiveFile);
                log.debug("Copying %s from %s container into %s host file", containerPath, containerId, archiveFile);
                dockerAccess.copyArchiveFromContainer(containerId, containerPath, archiveFile);
                log.debug("Extracting %s archive into %s directory", archiveFile, hostDirectory);
                archiveService.extractDockerCopyArchive(archiveFile, hostDirectory);
            }
        }
    }

    private File getHostDirectory(String hostPath) {
        File projectBaseDirectory = project.getBasedir();
        if (hostPath == null) {
            return projectBaseDirectory;
        }
        File hostDirectory = new File(hostPath);
        if (hostDirectory.isAbsolute()) {
            return hostDirectory;
        }
        return new File(projectBaseDirectory, hostPath);
    }

    private static class ContainerRemover implements AutoCloseable {

        private final Logger logger;
        private final RunService runService;
        private final boolean removeVolumes;
        private String containerId;

        public ContainerRemover(Logger logger, RunService runService, boolean removeVolumes) {
            this.logger = logger;
            this.runService = runService;
            this.removeVolumes = removeVolumes;
        }

        public void setContainerId(String containerId) {
            this.containerId = containerId;
        }

        @Override
        public void close() throws IOException {
            if (containerId != null) {
                logger.debug("Removing %s container", containerId);
                runService.removeContainer(containerId, removeVolumes);
                containerId = null;
            }
        }
    }

    private static class FileRemover implements AutoCloseable {

        private final Logger logger;
        private File file;

        public FileRemover(Logger logger) {
            this.logger = logger;
        }

        public void setFile(File file) {
            this.file = file;
        }

        @Override
        public void close() throws IOException {
            if (file != null) {
                logger.debug("Removing %s file", file);
                Files.delete(file.toPath());
                file = null;
            }
        }
    }
}
