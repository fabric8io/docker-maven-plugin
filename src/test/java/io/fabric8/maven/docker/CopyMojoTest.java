package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.PortMapping;
import io.fabric8.maven.docker.config.CopyConfiguration.Entry;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.model.Container;
import io.fabric8.maven.docker.service.ImagePullManager;
import io.fabric8.maven.docker.service.RegistryService.RegistryConfig;
import io.fabric8.maven.docker.service.RunService.ContainerDescriptor;
import io.fabric8.maven.docker.util.ContainerNamingUtil;
import io.fabric8.maven.docker.util.GavLabel;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.fabric8.maven.docker.AbstractDockerMojo.CONTEXT_KEY_START_CALLED;

@ExtendWith(MockitoExtension.class)
class CopyMojoTest extends MojoTestBase {

    private static final String ANY_CONTAINER_NAME_PATTERN = "%regex[.*]";

    @InjectMocks
    private CopyMojo copyMojo;

    @Test
    void copyWithCreateContainersButNoImages() throws IOException, MojoExecutionException {
        givenMavenProject();
        givenCreateContainersIsTrue();

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    void copyWithCreateContainersButNoCopyConfiguration() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());
        givenCreateContainersIsTrue();

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    void copyWithCreateContainersButUndefinedCopyEntries() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithCopy(null));
        givenCreateContainersIsTrue();

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    void copyWithCreateContainersButNoCopyEntries() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithCopy(Collections.emptyList()));
        givenCreateContainersIsTrue();

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    void copyWithStartGoalInvokedButNoContainersTracked() throws IOException, MojoExecutionException {
        givenProjectWithStartGoalInvoked();
        givenNoContainersTracked();

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    void copyWithStartGoalInvokedButNoCopyConfiguration() throws IOException, MojoExecutionException {
        givenProjectWithStartGoalInvoked();
        givenTrackedContainer(singleContainerDescriptor(singleImageWithBuild(), "some-container"));

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    void copyWithStartGoalInvokedButNoCopyEntries() throws IOException, MojoExecutionException {
        givenProjectWithStartGoalInvoked();
        givenTrackedContainer(singleContainerDescriptor(singleImageWithCopy(Collections.emptyList()), "example"));

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    void copyButNoImages() throws IOException, MojoExecutionException {
        givenMavenProject();

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    void copyButNoCopyConfiguration() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    void copyButNoCopyEntries() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithCopy(Collections.emptyList()));

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    void copyButNoContainers() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithCopy(singleCopyEntry("containerPath", "hostDirectory")));
        givenNoContainerFound();

        whenMojoExecutes();

        thenNoContainerIsCreated();
        thenCopyArchiveFromContainerIsNotCalled();
    }

    @Test
    void copyWithCreateContainersAndAbsoluteHostDirectory() throws IOException, MojoExecutionException {
        final String pullRegistry = "some-docker-registry";
        final String containerPath = "/container/path/to/some/directory";
        final File hostDirectory = temporaryFolder.resolve("absolute-host-directory").toFile();
        final ImageConfiguration image = singleImageWithCopy(
            singleCopyEntry(containerPath, hostDirectory.getAbsolutePath()));
        final String containerNamePattern = "%i";
        final String temporaryContainerId = "some-test-container";

        givenProjectWithResolvedImage(image);
        givenCreateContainersIsTrue();
        givenPullRegistry(pullRegistry);
        givenContainerNamePattern(containerNamePattern);
        givenCreatedContainerId(temporaryContainerId);

        whenMojoExecutes();

        thenMissingImageIsPulled(image, pullRegistry);
        thenContainerIsCreated(image, containerNamePattern);
        thenContainerPathIsCopied(temporaryContainerId, containerPath, hostDirectory);
        thenContainerIsRemoved(temporaryContainerId);
    }

    @Test
    void copyWithCreateContainersAndRelativeHostDirectory() throws IOException, MojoExecutionException {
        final String pullRegistry = "another-docker-registry";
        final String containerPath = "/some/path/to/test/file.txt";
        final String hostDirectory = "project-base-dir-relative-host-directory";
        final ImageConfiguration image = singleImageWithCopy(singleCopyEntry(containerPath, hostDirectory));
        final String containerNamePattern = "temporary-container-name-pattern";
        final String temporaryContainerId = "one-more-test-container";

        givenProjectWithResolvedImage(image);
        givenCreateContainersIsTrue();
        givenPullRegistry(pullRegistry);
        givenContainerNamePattern(containerNamePattern);
        givenCreatedContainerId(temporaryContainerId);

        whenMojoExecutes();

        thenExistingImageIsPulled(image, pullRegistry);
        thenContainerIsCreated(image, containerNamePattern);
        thenContainerPathIsCopied(temporaryContainerId, containerPath, new File(projectBaseDirectory, hostDirectory));
        thenContainerIsRemoved(temporaryContainerId);
    }

    @Test
    void copyWithCreateContainersAndUndefinedHostDirectory() throws IOException, MojoExecutionException {
        final String containerPath = "/absolute/path/to/some/container/filesystem/resource";
        final ImageConfiguration image = singleImageWithCopy(singleCopyEntry(containerPath, null));
        final String containerNamePattern = ContainerNamingUtil.DEFAULT_CONTAINER_NAME_PATTERN;
        final String temporaryContainerId = "another-test-container";

        givenProjectWithResolvedImage(image);
        givenCreateContainersIsTrue();
        givenContainerNamePattern(containerNamePattern);
        givenCreatedContainerId(temporaryContainerId);

        whenMojoExecutes();

        thenExistingImageIsPulled(image, copyMojo.getPullRegistry());
        thenContainerIsCreated(image, containerNamePattern);
        thenContainerPathIsCopied(temporaryContainerId, containerPath, projectBaseDirectory);
        thenContainerIsRemoved(temporaryContainerId);
    }

    @Test
    void copyWithCreateContainersAndDefaultContainerNamePattern() throws IOException, MojoExecutionException {
        final String containerPath = "/resource";
        final ImageConfiguration image = singleImageWithCopy(singleCopyEntry(containerPath, null));
        final String temporaryContainerId = "another-test-container";

        givenProjectWithResolvedImage(image);
        givenCreateContainersIsTrue();
        givenCreatedContainerId(temporaryContainerId);

        whenMojoExecutes();

        thenExistingImageIsPulled(image, copyMojo.getPullRegistry());
        thenContainerWithNotEmptyNamePatternIsCreated(image);
        thenContainerPathIsCopied(temporaryContainerId, containerPath, projectBaseDirectory);
        thenContainerIsRemoved(temporaryContainerId);
    }

    @Test
    void copyWithCreateContainersButExceptionWhenCopying() throws IOException, MojoExecutionException {
        final String containerPath = "/any/container/resource";
        final ImageConfiguration image = singleImageWithCopy(singleCopyEntry(containerPath, null));
        final String containerNamePattern = "constant-container-name";
        final String temporaryContainerId = "some-container-id";
        final RuntimeException copyException = new RuntimeException("Test exception when copying from container");

        givenProjectWithResolvedImage(image);
        givenCreateContainersIsTrue();
        givenContainerNamePattern(containerNamePattern);
        givenCreatedContainerId(temporaryContainerId);
        givenExceptionWhenCopyingArchiveFromContainer(temporaryContainerId, copyException);

        RuntimeException caught= Assertions.assertThrows(RuntimeException.class, () ->whenMojoExecutes());
        Assertions.assertSame(copyException, caught);

        thenExistingImageIsPulled(image, copyMojo.getPullRegistry());
        thenContainerIsCreated(image, containerNamePattern);
        thenContainerIsRemoved(temporaryContainerId);
    }

    @Test
    void copyWithCreateContainersButExceptionWhenExtractingArchive() throws IOException, MojoExecutionException {
        final String containerPath = "/another/container/resource";
        final ImageConfiguration image = singleImageWithCopy(singleCopyEntry(containerPath, null));
        final String containerNamePattern = "container-name-pattern";
        final String temporaryContainerId = "created-container-id";
        final Exception extractException = new RuntimeException("Test exception when extracting copied archive");

        givenProjectWithResolvedImage(image);
        givenCreateContainersIsTrue();
        givenContainerNamePattern(containerNamePattern);
        givenCreatedContainerId(temporaryContainerId);
        givenExceptionWhenExtractingArchive(extractException);

        try {
            whenMojoExecutes();
            Assertions.fail();
        } catch (MojoExecutionException e) {
            Assertions.assertEquals(extractException, e.getCause());
        } catch (Exception e) {
            Assertions.assertEquals(extractException, e);
        }

        thenExistingImageIsPulled(image, copyMojo.getPullRegistry());
        thenContainerIsCreated(image, containerNamePattern);
        thenCopiedArchiveIsRemoved(temporaryContainerId, containerPath);
        thenContainerIsRemoved(temporaryContainerId);
    }

    @Test
    void copyWithCreateContainersButUndefinedContainerPath() throws IOException, MojoExecutionException {
        final ImageConfiguration image = singleImageWithCopy(singleCopyEntry(null, null));
        final String containerNamePattern = "%i";
        final String temporaryContainerId = "container-id";

        givenProjectWithResolvedImage(image);
        givenCreateContainersIsTrue();
        givenContainerNamePattern(containerNamePattern);
        givenCreatedContainerId(temporaryContainerId);

        try {
            whenMojoExecutes();
            Assertions.fail();
        } catch (Exception ignored) {
            // Nothing to check
        }

        thenExistingImageIsPulled(image, copyMojo.getPullRegistry());
        thenContainerIsCreated(image, containerNamePattern);
        thenCopyArchiveFromContainerIsNotCalled();
        thenContainerIsRemoved(temporaryContainerId);
    }

    @Test
    void copyWithStartGoalInvoked() throws IOException, MojoExecutionException {
        final String containerPath = "/container/test/path";
        final ImageConfiguration image = singleImageWithCopy(singleCopyEntry(containerPath, null));
        final String trackedContainerId = "tracked-container-id";

        givenProjectWithStartGoalInvoked(image);
        givenTrackedContainer(singleContainerDescriptor(image, trackedContainerId));

        whenMojoExecutes();

        thenNoContainerIsCreated();
        thenContainerPathIsCopied(trackedContainerId, containerPath, projectBaseDirectory);
    }

    @Test
    void copyWithUndefinedCopyNamePattern() throws IOException, MojoExecutionException {
        final String containerPath = "/container/test/path";
        final String hostDirectory = "project-base-dir-relative-host-directory";
        final ImageConfiguration image = singleImageWithCopy(singleCopyEntry(containerPath, hostDirectory));
        final String containerId = "test-container-id";

        givenProjectWithResolvedImage(image);
        givenMatchingLatestContainer(singleContainer(image, new Date().getTime(), containerId, false));

        whenMojoExecutes();

        thenNoContainerIsCreated();
        thenContainerPathIsCopied(containerId, containerPath, new File(projectBaseDirectory, hostDirectory));
    }

    @Test
    void copyFromTheLatestContainer() throws IOException, MojoExecutionException {
        final String containerPath = "/container/test/path";
        final String hostDirectory = "project-base-dir-relative-host-directory";
        final ImageConfiguration image = singleImageWithCopyNamePatternAndCopyEntries(ANY_CONTAINER_NAME_PATTERN,
            singleCopyEntry(containerPath, hostDirectory));
        final String containerId = "latest-container-id";

        givenProjectWithResolvedImage(image);
        givenMatchingLatestContainer(singleContainer(image, 1, containerId, false));

        whenMojoExecutes();

        thenNoContainerIsCreated();
        thenContainerPathIsCopied(containerId, containerPath, new File(projectBaseDirectory, hostDirectory));
    }

    @Test
    void copyAllWithUndefinedCopyNamePattern() throws IOException, MojoExecutionException {
        final String containerPath = "/container/test/path";
        final String hostDirectory = "project-base-dir-relative-host-directory";
        final ImageConfiguration image = singleImageWithCopy(singleCopyEntry(containerPath, hostDirectory));
        final String container1Id = "test-container1-id";
        final String container2Id = "test-container2-id";

        givenProjectWithCopyAll(image);
        givenMatchingContainers(Arrays.asList(singleContainer(image, 1, container1Id, false),
            singleContainer(image, 1, container2Id, false)));

        whenMojoExecutes();

        thenNoContainerIsCreated();
        thenContainersPathIsCopied(Arrays.asList(container1Id, container2Id), containerPath,
            new File(projectBaseDirectory, hostDirectory));
    }

    @Test
    void copyAll() throws IOException, MojoExecutionException {
        final String containerPath = "/container/test/path";
        final String hostDirectory = "project-base-dir-relative-host-directory";
        final ImageConfiguration image = singleImageWithCopyNamePatternAndCopyEntries(ANY_CONTAINER_NAME_PATTERN,
            singleCopyEntry(containerPath, hostDirectory));
        final String container1Id = "test-container1-id";
        final String container2Id = "test-container2-id";

        givenProjectWithCopyAll(image);
        givenMatchingContainers(Arrays.asList(singleContainer(image, 1, container1Id, false),
            singleContainer(image, 1, container2Id, false)));

        whenMojoExecutes();

        thenNoContainerIsCreated();
        thenContainersPathIsCopied(Arrays.asList(container1Id, container2Id), containerPath,
            new File(projectBaseDirectory, hostDirectory));
    }

    private void givenMavenProject() {
        givenMavenProject(copyMojo);
    }

    private void givenProjectWithStartGoalInvoked() {
        givenMavenProject();
        givenPluginContext(copyMojo, CONTEXT_KEY_START_CALLED, true);
    }

    private void givenProjectWithCopyAll(ImageConfiguration image) {
        givenMavenProject();
        givenResolvedImages(copyMojo, Collections.singletonList(image));
        copyMojo.copyAll = true;
    }

    private void givenProjectWithResolvedImage(ImageConfiguration image) {
        givenMavenProject();
        givenResolvedImages(copyMojo, Collections.singletonList(image));
    }

    private void givenProjectWithStartGoalInvoked(ImageConfiguration image) {
        givenProjectWithStartGoalInvoked();
        givenResolvedImages(copyMojo, Collections.singletonList(image));
    }

    private List<Entry> singleCopyEntry(String containerPath, String hostDirectory) {
        return Collections.singletonList(new Entry(containerPath, hostDirectory));
    }

    private void givenCreateContainersIsTrue() {
        copyMojo.createContainers = true;
    }

    private void givenPullRegistry(String pullRegistry) {
        copyMojo.pullRegistry = pullRegistry;
    }

    private void givenNoContainersTracked() {
        Mockito.lenient().doReturn(Collections.emptyList())
            .when(runService).getContainers(Mockito.any(GavLabel.class));
    }

    private void givenTrackedContainer(ContainerDescriptor container) {
        Mockito.lenient().doReturn(Collections.singletonList(container))
            .when(runService).getContainers(Mockito.any(GavLabel.class));
    }

    private void givenContainerNamePattern(String containerNamePattern) {
        copyMojo.containerNamePattern = containerNamePattern;
    }

    private void givenCreatedContainerId(String containerId) throws DockerAccessException {
        Mockito.doReturn(containerId)
            .when(runService).createContainer(Mockito.any(ImageConfiguration.class), Mockito.any(), Mockito.any(GavLabel.class),
                Mockito.any(Properties.class), Mockito.any(File.class), Mockito.anyString(), Mockito.any(Date.class));
    }

    private void givenExceptionWhenCopyingArchiveFromContainer(String containerId, Exception exception)
        throws DockerAccessException {
        Mockito.doThrow(exception)
            .when(dockerAccess).copyArchiveFromContainer(Mockito.eq(containerId), Mockito.anyString(), Mockito.any(File.class));
    }

    private void givenExceptionWhenExtractingArchive(Exception exception) throws MojoExecutionException {
        Mockito.doThrow(exception)
            .when(archiveService).extractDockerCopyArchive(Mockito.any(File.class), Mockito.any(File.class));
    }

    private void givenNoContainerFound() throws DockerAccessException {
        Mockito.lenient().doReturn(null)
            .when(queryService).getContainersForImage(Mockito.anyString(), Mockito.anyBoolean());
        Mockito.lenient().doReturn(Collections.emptyList())
            .when(queryService).listContainers(Mockito.anyBoolean());
        Mockito.lenient().doReturn(null)
            .when(queryService).getLatestContainerForImage(Mockito.anyString(), Mockito.anyBoolean());
    }

    private void givenMatchingContainers(List<Container> containers) throws DockerAccessException {
        Mockito.lenient().doReturn(containers)
            .when(queryService).getContainersForImage(Mockito.anyString(), Mockito.anyBoolean());
        Mockito.lenient().doReturn(containers)
            .when(queryService).listContainers(Mockito.anyBoolean());
    }

    private void givenMatchingLatestContainer(Container container) throws DockerAccessException {
        givenMatchingContainers(Collections.singletonList(container));

        Mockito.lenient().doReturn(container)
            .when(queryService).getLatestContainerForImage(Mockito.anyString(), Mockito.anyBoolean());
        Mockito.lenient().doReturn(container)
            .when(queryService).getLatestContainer(Mockito.any(List.class));
    }

    private ContainerDescriptor singleContainerDescriptor(ImageConfiguration imageConfiguration, String containerId) {
        return new ContainerDescriptor(containerId, imageConfiguration);
    }

    private Container singleContainer(ImageConfiguration imageConfiguration, long created, String containerId,
        boolean running) {
        return new Container() {
            @Override
            public long getCreated() {
                return created;
            }

            @Override
            public String getId() {
                return containerId;
            }

            @Override
            public String getImage() {
                return imageConfiguration.getName();
            }

            @Override
            public Map<String, String> getLabels() {
                return Collections.emptyMap();
            }

            @Override
            public String getName() {
                return containerId;
            }

            @Override
            public String getNetworkMode() {
                return null;
            }

            @Override
            public Map<String, PortBinding> getPortBindings() {
                return Collections.emptyMap();
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public String getIPAddress() {
                return null;
            }

            @Override
            public Map<String, String> getCustomNetworkIpAddresses() {
                return Collections.emptyMap();
            }

            @Override
            public Integer getExitCode() {
                return null;
            }
        };
    }

    private void whenMojoExecutes() throws IOException, MojoExecutionException {
        copyMojo.executeInternal(serviceHub);
    }

    private void thenNothingHappens() throws DockerAccessException {
        thenNoContainerLookupOccurs();
        thenNoContainerIsCreated();
        thenCopyArchiveFromContainerIsNotCalled();
    }

    private void thenNoContainerLookupOccurs() throws DockerAccessException {
        thenListContainersIsNotCalled();
        thenGetLatestContainerIsNotCalled();
        thenNoContainerLookupByImageOccurs();
        thenNoLatestContainerLookupByImageOccurs();
    }

    private void thenListContainersIsNotCalled() throws DockerAccessException {
        Mockito.verify(queryService, Mockito.never()).listContainers(Mockito.anyBoolean());
    }

    @SuppressWarnings("unchecked")
    private void thenGetLatestContainerIsNotCalled() {
        Mockito.verify(queryService, Mockito.never()).getLatestContainer(Mockito.any(List.class));
    }

    private void thenNoContainerLookupByImageOccurs() throws DockerAccessException {
        Mockito.verify(queryService, Mockito.never()).getContainersForImage(Mockito.anyString(), Mockito.anyBoolean());
    }

    private void thenNoLatestContainerLookupByImageOccurs() throws DockerAccessException {
        Mockito.verify(queryService, Mockito.never()).getLatestContainerForImage(Mockito.anyString(), Mockito.anyBoolean());
    }

    private void thenNoContainerIsCreated() throws DockerAccessException {
        Mockito.verify(runService, Mockito.never())
            .createContainer(Mockito.any(ImageConfiguration.class), Mockito.any(PortMapping.class), Mockito.any(GavLabel.class), Mockito.any(Properties.class),
                Mockito.any(File.class), Mockito.anyString(), Mockito.any(Date.class));
    }

    private void thenCopyArchiveFromContainerIsNotCalled() throws DockerAccessException {
        Mockito.verify(dockerAccess, Mockito.never()).copyArchiveFromContainer(Mockito.anyString(), Mockito.anyString(), Mockito.any(File.class));
    }

    private void thenMissingImageIsPulled(ImageConfiguration image, String pullRegistry)
        throws DockerAccessException, MojoExecutionException {
        thenImageIsPulled(image, pullRegistry, false);
    }

    private void thenExistingImageIsPulled(ImageConfiguration image, String pullRegistry)
        throws DockerAccessException, MojoExecutionException {
        thenImageIsPulled(image, pullRegistry, true);
    }

    private void thenImageIsPulled(ImageConfiguration image, String pullRegistry, boolean imageExists)
        throws DockerAccessException, MojoExecutionException {

        ArgumentCaptor<String> pulledImage = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RegistryConfig> registryCapture = ArgumentCaptor.forClass(RegistryConfig.class);
        Mockito.verify(registryService)
            .pullImageWithPolicy(pulledImage.capture(), Mockito.any(ImagePullManager.class), registryCapture.capture(), Mockito.eq(image.getBuildConfiguration()));

        Assertions.assertEquals(image.getName(), pulledImage.getValue());
        RegistryConfig registryConfig = registryCapture.getValue();
        Assertions.assertNotNull(registryConfig);
        Assertions.assertEquals(pullRegistry, registryConfig.getRegistry());
    }

    private void thenContainerIsCreated(ImageConfiguration image, String namePattern) throws DockerAccessException {

        ArgumentCaptor<ImageConfiguration> imageCapture = ArgumentCaptor.forClass(ImageConfiguration.class);
        Mockito.verify(runService).createContainer(imageCapture.capture(), Mockito.any(), Mockito.eq(projectGavLabel),
            Mockito.any(Properties.class), Mockito.any(File.class), Mockito.eq(namePattern), Mockito.any(Date.class));

        Assertions.assertEquals(image.getName(), imageCapture.getValue().getName());
    }

    private void thenContainerWithNotEmptyNamePatternIsCreated(ImageConfiguration image) throws DockerAccessException {

        ArgumentCaptor<ImageConfiguration> imageCapture = ArgumentCaptor.forClass(ImageConfiguration.class);
        ArgumentCaptor<String> patternCapture = ArgumentCaptor.forClass(String.class);
        Mockito.verify(runService).createContainer(imageCapture.capture(), Mockito.any(), Mockito.eq(projectGavLabel),
            Mockito.any(Properties.class), Mockito.any(File.class), patternCapture.capture(), Mockito.any(Date.class));

        Assertions.assertEquals(image.getName(), imageCapture.getValue().getName());
        String defaultContainerNamePattern = patternCapture.getValue();
        Assertions.assertNotNull(defaultContainerNamePattern);
        Assertions.assertNotEquals(0, defaultContainerNamePattern.length());
    }

    private void thenContainerPathIsCopied(String containerId, String containerPath, File targetDirectory)
        throws DockerAccessException, MojoExecutionException {

        ArgumentCaptor<File> copiedCapture = ArgumentCaptor.forClass(File.class);
        Mockito.verify(dockerAccess).copyArchiveFromContainer(Mockito.eq(containerId), Mockito.eq(containerPath), copiedCapture.capture());

        File copied = copiedCapture.getValue();
        Assertions.assertNotNull(copied);

        ArgumentCaptor<File> archiveCapture = ArgumentCaptor.forClass(File.class);
        ArgumentCaptor<File> destCapture = ArgumentCaptor.forClass(File.class);
        Mockito.verify(archiveService).extractDockerCopyArchive(archiveCapture.capture(), destCapture.capture());

        File archive = archiveCapture.getValue();
        File destination = destCapture.getValue();
        assertAbsolutePathEquals(copied, archive);
        assertAbsolutePathEquals(targetDirectory, destination);
        Assertions.assertFalse(copied.exists());
    }

    private void thenContainersPathIsCopied(List<String> containerIds, String containerPath, File targetDirectory)
        throws DockerAccessException, MojoExecutionException {

        ArgumentCaptor<String> containerIdsCapture = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<File> copiedArchivesCapture = ArgumentCaptor.forClass(File.class);
        Mockito.verify(dockerAccess, Mockito.times(containerIds.size()))
            .copyArchiveFromContainer(containerIdsCapture.capture(), Mockito.eq(containerPath), copiedArchivesCapture.capture());

        List<String> copiedContainerIds = containerIdsCapture.getAllValues();
        List<File> copiedArchives = copiedArchivesCapture.getAllValues();
        Assertions.assertArrayEquals(containerIds.toArray(), copiedContainerIds.toArray());

        ArgumentCaptor<File> archivesCapture = ArgumentCaptor.forClass(File.class);
        ArgumentCaptor<File> destinationsCapture = ArgumentCaptor.forClass(File.class);
        Mockito.verify(archiveService, Mockito.times(containerIds.size())).extractDockerCopyArchive(archivesCapture.capture(), destinationsCapture.capture());

        List<File> archives = archivesCapture.getAllValues();
        Assertions.assertEquals(copiedArchives.size(), archives.size());
        final Iterator<File> expectedArchiveIterator = copiedArchives.iterator();
        for (File archive : archives) {
            assertAbsolutePathEquals(expectedArchiveIterator.next(), archive);
            Assertions.assertFalse(archive.exists());
        }

        List<File> destinations = destinationsCapture.getAllValues();
        Assertions.assertEquals(containerIds.size(), destinations.size());
        for (File destination : destinations) {
            assertAbsolutePathEquals(targetDirectory, destination);
        }
    }

    private void thenContainerIsRemoved(String containerId) throws DockerAccessException {
        ArgumentCaptor<String> containerIdCapture = ArgumentCaptor.forClass(String.class);
        Mockito.verify(runService).removeContainer(containerIdCapture.capture(), Mockito.anyBoolean());
        Assertions.assertEquals(containerId, containerIdCapture.getValue());
    }

    private void thenCopiedArchiveIsRemoved(String containerId, String containerPath) throws DockerAccessException {
        ArgumentCaptor<File> copiedCapture = ArgumentCaptor.forClass(File.class);
        Mockito.verify(dockerAccess).copyArchiveFromContainer(Mockito.eq(containerId), Mockito.eq(containerPath), copiedCapture.capture());

        File copied = copiedCapture.getValue();
        Assertions.assertNotNull(copied);
        Assertions.assertFalse(copied.exists());
    }
}
