package io.fabric8.maven.docker;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Tested;
import mockit.Verifications;

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

import static io.fabric8.maven.docker.AbstractDockerMojo.CONTEXT_KEY_START_CALLED;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class CopyMojoTest extends BaseMojoTest {

    private static final String ANY_CONTAINER_NAME_PATTERN = "%regex[.*]";

    @Tested
    private CopyMojo copyMojo;

    @Test
    public void copyWithCreateContainersButNoImages() throws IOException, MojoExecutionException {
        givenMavenProject();
        givenCreateContainersIsTrue();

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    public void copyWithCreateContainersButNoCopyConfiguration() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());
        givenCreateContainersIsTrue();

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    public void copyWithCreateContainersButUndefinedCopyEntries() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithCopy(null));
        givenCreateContainersIsTrue();

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    public void copyWithCreateContainersButNoCopyEntries() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithCopy(Collections.emptyList()));
        givenCreateContainersIsTrue();

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    public void copyWithStartGoalInvokedButNoContainersTracked() throws IOException, MojoExecutionException {
        givenProjectWithStartGoalInvoked();
        givenNoContainersTracked();

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    public void copyWithStartGoalInvokedButNoCopyConfiguration() throws IOException, MojoExecutionException {
        givenProjectWithStartGoalInvoked();
        givenTrackedContainer(singleContainerDescriptor(singleImageWithBuild(), "some-container"));

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    public void copyWithStartGoalInvokedButNoCopyEntries() throws IOException, MojoExecutionException {
        givenProjectWithStartGoalInvoked();
        givenTrackedContainer(singleContainerDescriptor(singleImageWithCopy(Collections.emptyList()), "example"));

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    public void copyButNoImages() throws IOException, MojoExecutionException {
        givenMavenProject();

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    public void copyButNoCopyConfiguration() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    public void copyButNoCopyEntries() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithCopy(Collections.emptyList()));

        whenMojoExecutes();

        thenNothingHappens();
    }

    @Test
    public void copyButNoContainers() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithCopy(singleCopyEntry("containerPath", "hostDirectory")));
        givenNoContainerFound();

        whenMojoExecutes();

        thenNoContainerIsCreated();
        thenCopyArchiveFromContainerIsNotCalled();
    }

    @Test
    public void copyWithCreateContainersAndAbsoluteHostDirectory() throws IOException, MojoExecutionException {
        final String pullRegistry = "some-docker-registry";
        final String containerPath = "/container/path/to/some/directory";
        final File hostDirectory = temporaryFolder.newFolder("absolute-host-directory");
        final ImageConfiguration image = singleImageWithCopy(
                singleCopyEntry(containerPath, hostDirectory.getAbsolutePath()));
        final String containerNamePattern = "%i";
        final String temporaryContainerId = "some-test-container";

        givenProjectWithResolvedImage(image);
        givenCreateContainersIsTrue();
        givenPullRegistry(pullRegistry);
        givenContainerNamePattern(containerNamePattern);
        givenImageDoesntExist(image);
        givenCreatedContainerId(temporaryContainerId);

        whenMojoExecutes();

        thenMissingImageIsPulled(image, pullRegistry);
        thenContainerIsCreated(image, containerNamePattern);
        thenContainerPathIsCopied(temporaryContainerId, containerPath, hostDirectory);
        thenContainerIsRemoved(temporaryContainerId);
    }

    @Test
    public void copyWithCreateContainersAndRelativeHostDirectory() throws IOException, MojoExecutionException {
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
        givenImageExists(image);
        givenCreatedContainerId(temporaryContainerId);

        whenMojoExecutes();

        thenExistingImageIsPulled(image, pullRegistry);
        thenContainerIsCreated(image, containerNamePattern);
        thenContainerPathIsCopied(temporaryContainerId, containerPath, new File(projectBaseDirectory, hostDirectory));
        thenContainerIsRemoved(temporaryContainerId);
    }

    @Test
    public void copyWithCreateContainersAndUndefinedHostDirectory() throws IOException, MojoExecutionException {
        final String containerPath = "/absolute/path/to/some/container/filesystem/resource";
        final ImageConfiguration image = singleImageWithCopy(singleCopyEntry(containerPath, null));
        final String containerNamePattern = ContainerNamingUtil.DEFAULT_CONTAINER_NAME_PATTERN;
        final String temporaryContainerId = "another-test-container";

        givenProjectWithResolvedImage(image);
        givenCreateContainersIsTrue();
        givenContainerNamePattern(containerNamePattern);
        givenImageExists(image);
        givenCreatedContainerId(temporaryContainerId);

        whenMojoExecutes();

        thenExistingImageIsPulled(image, copyMojo.getPullRegistry());
        thenContainerIsCreated(image, containerNamePattern);
        thenContainerPathIsCopied(temporaryContainerId, containerPath, new File(projectBaseDirectory));
        thenContainerIsRemoved(temporaryContainerId);
    }

    @Test
    public void copyWithCreateContainersAndDefaultContainerNamePattern() throws IOException, MojoExecutionException {
        final String containerPath = "/resource";
        final ImageConfiguration image = singleImageWithCopy(singleCopyEntry(containerPath, null));
        final String temporaryContainerId = "another-test-container";

        givenProjectWithResolvedImage(image);
        givenCreateContainersIsTrue();
        givenImageExists(image);
        givenCreatedContainerId(temporaryContainerId);

        whenMojoExecutes();

        thenExistingImageIsPulled(image, copyMojo.getPullRegistry());
        thenContainerWithNotEmptyNamePatternIsCreated(image);
        thenContainerPathIsCopied(temporaryContainerId, containerPath, new File(projectBaseDirectory));
        thenContainerIsRemoved(temporaryContainerId);
    }

    @Test
    public void copyWithCreateContainersButExceptionWhenCopying() throws IOException, MojoExecutionException {
        final String containerPath = "/any/container/resource";
        final ImageConfiguration image = singleImageWithCopy(singleCopyEntry(containerPath, null));
        final String containerNamePattern = "constant-container-name";
        final String temporaryContainerId = "some-container-id";
        final Exception copyException = new RuntimeException("Test exception when copying from container");

        givenProjectWithResolvedImage(image);
        givenCreateContainersIsTrue();
        givenContainerNamePattern(containerNamePattern);
        givenImageExists(image);
        givenCreatedContainerId(temporaryContainerId);
        givenExceptionWhenCopyingArchiveFromContainer(temporaryContainerId, copyException);

        try {
            whenMojoExecutes();
            fail();
        } catch (MojoExecutionException e) {
            assertEquals(copyException, e.getCause());
        } catch (Exception e) {
            assertEquals(copyException, e);
        }

        thenExistingImageIsPulled(image, copyMojo.getPullRegistry());
        thenContainerIsCreated(image, containerNamePattern);
        thenContainerIsRemoved(temporaryContainerId);
    }

    @Test
    public void copyWithCreateContainersButExceptionWhenExtractingArchive() throws IOException, MojoExecutionException {
        final String containerPath = "/another/container/resource";
        final ImageConfiguration image = singleImageWithCopy(singleCopyEntry(containerPath, null));
        final String containerNamePattern = "container-name-pattern";
        final String temporaryContainerId = "created-container-id";
        final Exception extractException = new RuntimeException("Test exception when extracting copied archive");

        givenProjectWithResolvedImage(image);
        givenCreateContainersIsTrue();
        givenContainerNamePattern(containerNamePattern);
        givenImageExists(image);
        givenCreatedContainerId(temporaryContainerId);
        givenExceptionWhenExtractingArchive(extractException);

        try {
            whenMojoExecutes();
            fail();
        } catch (MojoExecutionException e) {
            assertEquals(extractException, e.getCause());
        } catch (Exception e) {
            assertEquals(extractException, e);
        }

        thenExistingImageIsPulled(image, copyMojo.getPullRegistry());
        thenContainerIsCreated(image, containerNamePattern);
        thenCopiedArchiveIsRemoved(temporaryContainerId, containerPath);
        thenContainerIsRemoved(temporaryContainerId);
    }

    @Test
    public void copyWithCreateContainersButUndefinedContainerPath() throws IOException, MojoExecutionException {
        final ImageConfiguration image = singleImageWithCopy(singleCopyEntry(null, null));
        final String containerNamePattern = "%i";
        final String temporaryContainerId = "container-id";

        givenProjectWithResolvedImage(image);
        givenCreateContainersIsTrue();
        givenContainerNamePattern(containerNamePattern);
        givenImageExists(image);
        givenCreatedContainerId(temporaryContainerId);

        try {
            whenMojoExecutes();
            fail();
        } catch (Exception ignored) {
            // Nothing to check
        }

        thenExistingImageIsPulled(image, copyMojo.getPullRegistry());
        thenContainerIsCreated(image, containerNamePattern);
        thenCopyArchiveFromContainerIsNotCalled();
        thenContainerIsRemoved(temporaryContainerId);
    }

    @Test
    public void copyWithStartGoalInvoked() throws IOException, MojoExecutionException {
        final String containerPath = "/container/test/path";
        final ImageConfiguration image = singleImageWithCopy(singleCopyEntry(containerPath, null));
        final String trackedContainerId = "tracked-container-id";

        givenProjectWithStartGoalInvoked(image);
        givenTrackedContainer(singleContainerDescriptor(image, trackedContainerId));

        whenMojoExecutes();

        thenNoContainerIsCreated();
        thenContainerPathIsCopied(trackedContainerId, containerPath, new File(projectBaseDirectory));
    }

    @Test
    public void copyWithUndefinedCopyNamePattern() throws IOException, MojoExecutionException {
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
    public void copyFromTheLatestContainer() throws IOException, MojoExecutionException {
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
    public void copyAllWithUndefinedCopyNamePattern() throws IOException, MojoExecutionException {
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
    public void copyAll() throws IOException, MojoExecutionException {
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
        givenCopyAll(copyMojo);
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
        Deencapsulation.setField(copyMojo, "createContainers", true);
    }

    private void givenPullRegistry(String pullRegistry) {
        Deencapsulation.setField(copyMojo, "pullRegistry", pullRegistry);
    }

    private void givenNoContainersTracked() {
        new Expectations() {{
            runService.getContainers((GavLabel) any);
            result = Collections.emptyList();
            minTimes = 0;
        }};
    }

    private void givenTrackedContainer(ContainerDescriptor container) {
        new Expectations() {{
            runService.getContainers((GavLabel) any);
            result = Collections.singletonList(container);
            minTimes = 0;
        }};
    }

    private void givenContainerNamePattern(String containerNamePattern) {
        Deencapsulation.setField(copyMojo, "containerNamePattern", containerNamePattern);
    }

    private void givenImageExists(ImageConfiguration image) throws DockerAccessException {
        givenImageExistence(image, true);
    }

    private void givenImageDoesntExist(ImageConfiguration image) throws DockerAccessException {
        givenImageExistence(image, false);
    }

    private void givenImageExistence(ImageConfiguration image, boolean imageExists) throws DockerAccessException {
        new Expectations() {{
            queryService.hasImage(image.getName());
            result = imageExists;
        }};
    }

    private void givenCreatedContainerId(String containerId) throws DockerAccessException {
        new Expectations() {{
            runService.createContainer((ImageConfiguration) any, (PortMapping) any, projectGavLabel, (Properties) any,
                    (File) any, anyString, (Date) any);
            result = containerId;
        }};
    }

    private void givenExceptionWhenCopyingArchiveFromContainer(String containerId, Exception exception)
            throws DockerAccessException {
        new Expectations() {{
            dockerAccess.copyArchiveFromContainer(containerId, anyString, (File) any);
            result = exception;
        }};
    }

    private void givenExceptionWhenExtractingArchive(Exception exception) throws MojoExecutionException {
        new Expectations() {{
            archiveService.extractDockerCopyArchive((File) any, (File) any);
            result = exception;
        }};
    }

    private void givenNoContainerFound() throws DockerAccessException {
        new Expectations() {{
            queryService.getContainersForImage(anyString, anyBoolean);
            minTimes = 0;
            result = null;
            queryService.listContainers(anyBoolean);
            minTimes = 0;
            result = Collections.emptyList();
            queryService.getLatestContainerForImage(anyString, anyBoolean);
            minTimes = 0;
            result = null;
        }};
    }

    private void givenMatchingContainers(List<Container> containers) throws DockerAccessException {
        new Expectations() {{
            queryService.getContainersForImage(anyString, anyBoolean);
            minTimes = 0;
            result = containers;
            queryService.listContainers(anyBoolean);
            minTimes = 0;
            result = containers;
        }};
    }

    private void givenMatchingLatestContainer(Container container) throws DockerAccessException {
        givenMatchingContainers(Collections.singletonList(container));
        new Expectations() {{
            queryService.getLatestContainerForImage(anyString, anyBoolean);
            minTimes = 0;
            result = container;
            queryService.getLatestContainer((List) any);
            minTimes = 0;
            result = container;
        }};
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
        new Verifications() {{
            queryService.listContainers(anyBoolean);
            times = 0;
        }};
    }

    @SuppressWarnings("unchecked")
    private void thenGetLatestContainerIsNotCalled() {
        new Verifications() {{
            queryService.getLatestContainer((List<Container>) any);
            times = 0;
        }};
    }

    private void thenNoContainerLookupByImageOccurs() throws DockerAccessException {
        new Verifications() {{
            queryService.getContainersForImage(anyString, anyBoolean);
            times = 0;
        }};
    }

    private void thenNoLatestContainerLookupByImageOccurs() throws DockerAccessException {
        new Verifications() {{
            queryService.getLatestContainerForImage(anyString, anyBoolean);
            times = 0;
        }};
    }

    private void thenNoContainerIsCreated() throws DockerAccessException {
        new Verifications() {{
            runService.createContainer((ImageConfiguration) any, (PortMapping) any, projectGavLabel, (Properties) any,
                    (File) any, anyString, (Date) any);
            times = 0;
        }};
    }

    private void thenCopyArchiveFromContainerIsNotCalled() throws DockerAccessException {
        new Verifications() {{
            dockerAccess.copyArchiveFromContainer(anyString, anyString, (File) any);
            times = 0;
        }};
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
        new Verifications() {{
            final String imageName;
            final RegistryConfig registryConfig;
            registryService.pullImageWithPolicy(imageName = withCapture(), (ImagePullManager) any,
                    registryConfig = withCapture(), imageExists);
            times = 1;
            assertEquals(image.getName(), imageName);
            assertNotNull(registryConfig);
            assertEquals(pullRegistry, registryConfig.getRegistry());
        }};
    }

    private void thenContainerIsCreated(ImageConfiguration image, String namePattern) throws DockerAccessException {
        new Verifications() {{
            final ImageConfiguration containerImage;
            runService.createContainer(containerImage = withCapture(), (PortMapping) any, projectGavLabel,
                    (Properties) any, (File) any, namePattern, (Date) any);
            times = 1;
            assertEquals(image.getName(), containerImage.getName());
        }};
    }

    private void thenContainerWithNotEmptyNamePatternIsCreated(ImageConfiguration image) throws DockerAccessException {
        new Verifications() {{
            final ImageConfiguration containerImage;
            final String defaultContainerNamePattern;
            runService.createContainer(containerImage = withCapture(), (PortMapping) any, projectGavLabel,
                    (Properties) any, (File) any, defaultContainerNamePattern = withCapture(), (Date) any);
            times = 1;
            assertEquals(image.getName(), containerImage.getName());
            assertNotNull(defaultContainerNamePattern);
            assertNotEquals(0, defaultContainerNamePattern.length());
        }};
    }

    private void thenContainerPathIsCopied(String containerId, String containerPath, File targetDirectory)
            throws DockerAccessException, MojoExecutionException {
        new Verifications() {{
            final File copied;
            dockerAccess.copyArchiveFromContainer(containerId, containerPath, copied = withCapture());
            times = 1;
            assertNotNull(copied);

            final File archive;
            final File destination;
            archiveService.extractDockerCopyArchive(archive = withCapture(), destination = withCapture());
            times = 1;
            assertAbsolutePathEquals(copied, archive);
            assertAbsolutePathEquals(targetDirectory, destination);
            assertFalse(copied.exists());
        }};
    }

    private void thenContainersPathIsCopied(List<String> containerIds, String containerPath, File targetDirectory)
            throws DockerAccessException, MojoExecutionException {
        new Verifications() {{
            final List<String> copiedContainerIds = new ArrayList<>();
            final List<File> copiedArchives = new ArrayList<>();
            dockerAccess.copyArchiveFromContainer(withCapture(copiedContainerIds), containerPath,
                    withCapture(copiedArchives));
            times = containerIds.size();
            assertArrayEquals(containerIds.toArray(), copiedContainerIds.toArray());

            final List<File> archives = new ArrayList<>();
            final List<File> destinations = new ArrayList<>();
            archiveService.extractDockerCopyArchive(withCapture(archives), withCapture(destinations));
            times = containerIds.size();

            assertEquals(copiedArchives.size(), archives.size());
            final Iterator<File> expectedArchiveIterator = copiedArchives.iterator();
            for (File archive : archives) {
                assertAbsolutePathEquals(expectedArchiveIterator.next(), archive);
                assertFalse(archive.exists());
            }

            assertEquals(containerIds.size(), destinations.size());
            for (File destination : destinations) {
                assertAbsolutePathEquals(targetDirectory, destination);
            }
        }};
    }

    private void thenContainerIsRemoved(String containerId) throws DockerAccessException {
        new Verifications() {{
            final String removedContainerId;
            runService.removeContainer(removedContainerId = withCapture(), anyBoolean);
            times = 1;
            assertEquals(containerId, removedContainerId);
        }};
    }

    private void thenCopiedArchiveIsRemoved(String containerId, String containerPath) throws DockerAccessException {
        new Verifications() {{
            final File copied;
            dockerAccess.copyArchiveFromContainer(containerId, containerPath, copied = withCapture());
            times = 1;
            assertNotNull(copied);
            assertFalse(copied.exists());
        }};
    }
}
