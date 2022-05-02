package io.fabric8.maven.docker;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.ExecException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.model.Container;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StopMojoTest extends MojoTestBase {
    
    @InjectMocks
    private StopMojo stopMojo;

    @Mock
    private Container runningInstance;

    /**
     * Mock project with no images, no containers are stopped.
     */
    @Test
    void stopWithNoImages() throws IOException, MojoExecutionException, ExecException {
        givenMavenProject();

        whenMojoExecutes();

        thenNoContainerLookupByImageOccurs();
        thenNoContainerIsStopped();
    }

    /**
     * Mock project with no images, but stopNamePattern is set, so containers are checked.
     */
    @Test
    void stopWithStopNamePatternButNoImages() throws IOException, MojoExecutionException, ExecException {
        givenMavenProject();
        givenStopNamePattern("**/example:*");

        whenMojoExecutes();

        thenNoContainerLookupByImageOccurs();
        thenListContainersIsCalled();
        thenNoContainerIsStopped();
    }

    /**
     * Mock project with no images, but stopNamePattern is set to a list that evaluates as empty,
     * so containers are checked.
     */
    @Test
    void stopWithStopNamePatternThatIsActuallyEmpty() throws IOException, MojoExecutionException, ExecException {
        givenMavenProject();
        givenStopNamePattern(" , , ");

        whenMojoExecutes();

        thenNoContainerLookupByImageOccurs();
        thenListContainersIsNotCalled();
        thenNoContainerIsStopped();
    }

    /**
     * Mock project with no images, but stopNamePattern is set, so a container is stopped.
     */
    @Test
    void stopWithMatchingStopNamePatternButNoImages() throws IOException, MojoExecutionException, ExecException {
        givenMavenProject();
        givenStopNamePattern("**/example:*");

        givenRunningContainer("container-id", "example-1", "example:latest");
        givenContainerHasGavLabels();
        givenListOfRunningContainers();

        whenMojoExecutes();

        thenNoContainerLookupByImageOccurs();
        thenListContainersIsCalled();
        thenContainerIsStopped("container-id", false, false);
    }

    /**
     * Mock project with one image, query service indicates running image, but it is not labelled.
     */
    @Test
    void stopWithSingleImageNotLabelled() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuild());
        givenContainerIsRunningForImage("example:latest", "container-id", "example-1");
        // mocking will return empty label map by default, so the GAV label is not present

        whenMojoExecutes();

        thenContainerLookupByImageOccurs("example:latest");
        thenListContainersIsNotCalled();
        thenNoContainerIsStopped();
    }

    /**
     * Mock project with one image, query service indicates running image, and it is labelled.
     */
    @Test
    void stopWithSingleImageIsLabelled() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        givenContainerIsRunningForImage("example:latest", "container-id", "example-1");
        givenContainerHasGavLabels();

        whenMojoExecutes();

        thenContainerLookupByImageOccurs("example:latest");
        thenListContainersIsNotCalled();
        thenContainerIsStopped("container-id", false, false);
    }

    /**
     * Mock project with one image, query service indicates running image, and it is labelled.
     * Removal should pass true for removeVolumes.
     */
    @Test
    void stopWithSingleImageIsLabelledRemoveVolume() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        givenContainerIsRunningForImage("example:latest", "container-id", "example-1");
        givenContainerHasGavLabels();

        stopMojo.removeVolumes= true;

        whenMojoExecutes();

        thenContainerLookupByImageOccurs("example:latest");
        thenListContainersIsNotCalled();
        thenContainerIsStopped("container-id", false, true);
    }

    /**
     * Mock project with one image, query service indicates running image, and it is labelled.
     * Removal should pass true for keepContainers, and query should exclude stopped containers.
     */
    @Test
    void stopWithSingleImageIsLabelledKeepingContainers() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        givenContainerIsRunningForImage("example:latest", "container-id", "example-1");
        givenContainerHasGavLabels();

        stopMojo.keepContainer= true;

        whenMojoExecutes();

        thenContainerLookupByImageOccurs("example:latest");
        thenListContainersIsNotCalled();
        thenContainerIsStopped("container-id", true, false);
    }

    /**
     * Mock project with one image, query service indicates running image, which is not labelled,
     * but allContainers is true.
     */
    @Test
    void stopWithSingleImageNotLabelledButAllContainersTrue() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        givenContainerIsRunningForImage("example:latest", "container-id", "example-1");
        // don't configure GAV labels

        givenAllContainersIsTrue();

        whenMojoExecutes();

        thenContainerLookupByImageOccurs("example:latest");
        thenListContainersIsNotCalled();
        thenContainerIsStopped("container-id", false, false);
    }

    /**
     * Mock project with one image, query service indicates three running images, none labelled,
     * but allContainers is true.
     * <p>
     * The containers are named with ascending indices - only the last should be stopped.
     */
    @Test
    void stopWithMultipleImagesCheckLast(@Mock Container running1, @Mock Container running2, @Mock Container running3)
        throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        givenAllContainersIsTrue();

        givenRunningContainer(running1, "container-id-1", "example-1");
        givenRunningContainer(running2, "container-id-2", "example-2");
        givenRunningContainer(running3, "container-id-3", "example-3");
        givenContainersAreRunningForImage("example:latest", running1, running2, running3);

        whenMojoExecutes();

        thenContainerLookupByImageOccurs("example:latest");
        thenListContainersIsNotCalled();
        thenContainerIsNotStopped("container-id-1");
        thenContainerIsNotStopped("container-id-2");
        thenContainerIsStopped("container-id-3", false, false);
    }

    /**
     * Mock project with one image, query service indicates three running images, none labelled,
     * but allContainers is true.
     * <p>
     * The containers are named with ascending indices, but there's a gap, so the last before
     * the gap and all the ones after are stopped.
     */
    @Test
    void stopWithMultipleImagesIndexPatternMissingIndices(@Mock Container running1, @Mock Container running2, @Mock Container running3)
        throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        givenAllContainersIsTrue();

        givenRunningContainer(running1, "container-id-1", "example-1");
        givenRunningContainer(running2, "container-id-2", "example-2");
        // having a gap in the numbering causes the enumeration of images to stop early
        givenRunningContainer(running3, "container-id-4", "example-4");
        givenContainersAreRunningForImage("example:latest", running1, running2, running3);

        whenMojoExecutes();

        thenContainerLookupByImageOccurs("example:latest");
        thenListContainersIsNotCalled();
        thenContainerIsNotStopped("container-id-1");
        thenContainerIsStopped("container-id-2", false, false);
        thenContainerIsStopped("container-id-4", false, false);
    }

    /**
     * Mock project with one image, query service indicates three running images, none labelled,
     * but allContainers is true.
     * <p>
     * One container matches the name pattern and the others not, so that not all containers are stopped.
     */
    @Test
    void stopWithMultipleImagesNoIndexNamePattern(@Mock Container running1, @Mock Container running2, @Mock Container running3)
        throws IOException, MojoExecutionException, ExecException {
        this.consoleLogger.setThreshold(0);
        givenProjectWithResolvedImage(singleImageWithBuild());

        givenAllContainersIsTrue();

        givenRunningContainer(running1, "container-id-1", "example");
        givenRunningContainer(running2, "container-id-2", "example-2");
        givenRunningContainer(running3, "container-id-3", "example-3");
        givenContainersAreRunningForImage("example:latest", running1, running2, running3);

        // If name pattern doesn't contain index placeholder then matching containers are stopped
        stopMojo.containerNamePattern= "%n";

        whenMojoExecutes();

        thenContainerLookupByImageOccurs("example:latest");
        thenListContainersIsNotCalled();
        thenContainerIsStopped("container-id-1", false, false);
        thenContainerIsNotStopped("container-id-2");
        thenContainerIsNotStopped("container-id-3");
    }

    /**
     * Mock project with one image, query service indicates three running images, none labelled,
     * but allContainers is true.
     * <p>
     * The containers are named with ascending indices - but this doesn't match the container
     * name pattern, so all are stopped.
     */
    @Test
    void stopWithAliasNamePattern(@Mock Container running1, @Mock Container running2,
        @Mock Container running3) throws IOException, MojoExecutionException, ExecException {
        this.consoleLogger.setThreshold(0);
        givenProjectWithResolvedImage(singleImageWithRunAndAlias("example-2"));

        givenAllContainersIsTrue();

        givenRunningContainer(running1, "container-id-1", "example-1");
        givenRunningContainer(running2, "container-id-2", "example-2");
        givenRunningContainer(running3, "container-id-3", "example-3");
        givenContainersAreRunningForImage("example:latest", running1, running2, running3);

        // If name pattern doesn't contain index placeholder, all containers are stopped
        stopMojo.containerNamePattern= "%a";

        whenMojoExecutes();

        thenContainerLookupByImageOccurs("example:latest");
        thenListContainersIsNotCalled();
        thenContainerIsNotStopped("container-id-1");
        thenContainerIsStopped("container-id-2", false, false);
        thenContainerIsNotStopped("container-id-3");
    }

    /**
     * Mock project with one image, query service indicates running image, but it is not labelled.
     * The stopNamePattern is set and does not match.
     */
    @Test
    void stopWithSingleImageAndPatternDoesNotMatch() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuildAndStopNamePattern("**/example:loudest"));

        givenRunningContainer("container-id-1", "example-1", "example:latest");

        givenListOfRunningContainers();

        whenMojoExecutes();

        thenNoContainerLookupByImageOccurs();
        thenListContainersIsCalled();
        thenNoContainerIsStopped();
    }

    /**
     * Mock project with one image, query service indicates running image, but it is not labelled.
     * The stopNamePattern is set for images only and does not match.
     */
    @Test
    void stopWithSingleImageAndNoApplicablePattern() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuildAndStopNamePattern(" , , "));

        whenMojoExecutes();

        thenNoContainerLookupByImageOccurs();
        thenListContainersIsNotCalled();
        thenNoContainerIsStopped();
    }

    /**
     * Mock project with one image, query service indicates running image, but it is not labelled.
     * The stopNamePattern is set for images only and does not match.
     */
    @Test
    void stopWithSingleImageAndImagePatternSyntaxException() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuildAndStopNamePattern(" , image=%regex[surprise! [], "));

        Assertions.assertThrows(MojoExecutionException.class, this::whenMojoExecutes);

        thenNoContainerLookupByImageOccurs();
        thenListContainersIsNotCalled();
        thenNoContainerIsStopped();
    }

    /**
     * Mock project with one image, query service indicates running image, but it is not labelled.
     * The stopNamePattern is set for container names only and does not match.
     */
    @Test
    void stopWithSingleImageAndContainerNamePatternSyntaxException()
        throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuildAndStopNamePattern(" ,name= %regex[surprise! [], "));

        Assertions.assertThrows(MojoExecutionException.class, this::whenMojoExecutes);

        thenNoContainerLookupByImageOccurs();
        thenListContainersIsNotCalled();

        thenNoContainerIsStopped();
    }

    /**
     * Mock project with one image, query service indicates running image, but it is not labelled.
     * The stopNamePattern is set for images only and does not match.
     */
    @Test
    void stopWithSingleImageAndImageOnlyPattern() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuildAndStopNamePattern("image=**/example:loudest"));

        givenRunningContainer("container-id-1", "example-1", "example:latest");
        givenContainerHasGavLabels();
        givenListOfRunningContainers();

        whenMojoExecutes();

        thenNoContainerLookupByImageOccurs();
        thenListContainersIsCalled();
        thenNoContainerIsStopped();
    }

    /**
     * Mock project with one image, query service indicates running image, but it is not labelled.
     * The stopNamePattern is set for container names only and does not match.
     */
    @Test
    void stopWithSingleImageAndNameOnlyPattern() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuildAndStopNamePattern("name=exemplary-*"));

        givenRunningContainer("container-id-1", "example-1", "example:latest");
        givenContainerHasGavLabels();
        givenListOfRunningContainers();

        whenMojoExecutes();

        thenNoContainerLookupByImageOccurs();
        thenListContainersIsCalled();
        thenNoContainerIsStopped();
    }

    /**
     * Mock project with one image, query service indicates three running images, none labelled,
     * but allContainers is true.
     * <p>
     * The containers are named with ascending indices, but pattern matching is in effect, so
     * they are all stopped.
     */
    @ParameterizedTest
    @ValueSource(strings = {"example:*", "example-*", "name=%regex[example-[14]],image=*:v2,**:v3"})
    void stopWithMultipleImagesImageNamePattern(String stopNamePattern, @Mock Container running1, @Mock Container running2, @Mock Container running3)
        throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuildAndStopNamePattern(stopNamePattern));

        givenAllContainersIsTrue();

        givenRunningContainer(running1, "container-id-1", "example-1", "example:v1");
        givenRunningContainer(running2, "container-id-2", "example-2", "example:v2");
        givenRunningContainer(running3, "container-id-3", "example-3", "example:v3");
        givenListOfRunningContainers(running1, running2, running3);

        whenMojoExecutes();

        thenNoContainerLookupByImageOccurs();
        thenListContainersIsCalled();
        thenContainerIsStopped("container-id-1", false, false);
        thenContainerIsStopped("container-id-2", false, false);
        thenContainerIsStopped("container-id-3", false, false);
    }

    /**
     * Mock project with one image, query service indicates three running images, none labelled,
     * but allContainers is true.
     * <p>
     * The containers are named with ascending indices, but pattern matching is in effect, so
     * two of them are stopped.
     */
    @Test
    void stopWithMultipleImagesContainerNameRegexPattern(@Mock Container running1, @Mock Container running2, @Mock Container running3)
        throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuildAndStopNamePattern("%regex[example-[13]]"));

        givenAllContainersIsTrue();

        givenRunningContainer(running1, "container-id-1", "example-1", "example:v1");
        givenRunningContainer(running2, "container-id-2", "example-2", "example:v2");
        givenRunningContainer(running3, "container-id-3", "example-3", "example:v3");
        givenListOfRunningContainers(running1, running2, running3);

        whenMojoExecutes();

        thenNoContainerLookupByImageOccurs();
        thenListContainersIsCalled();
        thenContainerIsStopped("container-id-1", false, false);
        thenContainerIsNotStopped("container-id-2");
        thenContainerIsStopped("container-id-3", false, false);
    }

    private void givenMavenProject() {
        givenMavenProject(stopMojo);
    }

    private void givenProjectWithResolvedImage(ImageConfiguration image) {
        givenMavenProject(stopMojo);
        givenResolvedImages(stopMojo, Collections.singletonList(image));
    }

    protected ImageConfiguration singleImageWithBuildAndStopNamePattern(String stopNamePattern) {
        return new ImageConfiguration.Builder()
            .name("example:latest")
            .stopNamePattern(stopNamePattern)
            .buildConfig(new BuildImageConfiguration.Builder()
                .from("scratch")
                .build())
            .build();
    }

    private void givenStopNamePattern(String stopNamePattern) {
        stopMojo.stopNamePattern= stopNamePattern;
    }

    private void givenAllContainersIsTrue() {
        stopMojo.allContainers= true;
    }

    private void givenRunningContainer(String containerId, String containerName, String imageName) {
        givenRunningContainer(this.runningInstance, containerId, containerName, imageName);
    }

    private void givenRunningContainer(Container instance, String containerId, String containerName) {
        givenRunningContainer(instance, containerId, containerName, null);
    }

    private void givenRunningContainer(Container instance, String containerId, String containerName, String imageName) {
        Mockito.lenient().doReturn(containerId).when(instance).getId();
        Mockito.lenient().doReturn(containerName).when(instance).getName();
        Mockito.lenient().doReturn(imageName).when(instance).getImage();
    }

    private void givenListOfRunningContainers(Container... instances) throws DockerAccessException {
        Mockito.doReturn(instances.length == 0 ? Collections.singletonList(runningInstance) : Arrays.asList(instances))
            .when(queryService).listContainers(Mockito.anyBoolean());
    }

    private void givenContainerIsRunningForImage(String imageName, String containerId, String containerName) throws DockerAccessException {
        givenRunningContainer(this.runningInstance, containerId, containerName, imageName);
        givenContainersAreRunningForImage(imageName, this.runningInstance);
    }

    private void givenContainersAreRunningForImage(String imageName, Container... containers) throws DockerAccessException {
        Mockito.doReturn(Arrays.asList(containers))
            .when(queryService)
            .getContainersForImage(Mockito.eq(imageName), Mockito.anyBoolean());
    }

    private void givenContainerHasGavLabels() {
        givenContainerHasGavLabels(this.runningInstance);
    }

    private void givenContainerHasGavLabels(Container instance) {
        Mockito.lenient().doReturn(Collections.singletonMap(projectGavLabel.getKey(), projectGavLabel.getValue()))
            .when(instance).getLabels();
    }

    private void whenMojoExecutes() throws ExecException, IOException, MojoExecutionException {
        stopMojo.executeInternal(serviceHub);
    }

    private void thenNoContainerIsStopped() throws ExecException, DockerAccessException {
        Mockito.verify(runService, Mockito.never())
            .stopContainer(Mockito.anyString(), Mockito.any(ImageConfiguration.class), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    private void thenContainerIsStopped(String containerId, boolean keepContainer, boolean removeVolumes) throws ExecException, DockerAccessException {
        Mockito.verify(runService)
            .stopContainer(Mockito.eq(containerId), Mockito.any(ImageConfiguration.class), Mockito.eq(keepContainer), Mockito.eq(removeVolumes));
    }

    private void thenContainerIsNotStopped(String containerId) throws ExecException, DockerAccessException {
        Mockito.verify(runService, Mockito.never())
            .stopContainer(Mockito.eq(containerId), Mockito.any(ImageConfiguration.class), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    private void thenNoContainerLookupByImageOccurs() throws DockerAccessException {
        Mockito.verify(queryService, Mockito.never())
            .getContainersForImage(Mockito.anyString(), Mockito.anyBoolean());
    }

    private void thenContainerLookupByImageOccurs(String imageName) throws DockerAccessException {
        Mockito.verify(queryService)
            .getContainersForImage(Mockito.eq(imageName), Mockito.anyBoolean());
    }

    private void thenListContainersIsCalled() throws DockerAccessException {
        Mockito.verify(queryService)
            .listContainers( Mockito.anyBoolean());
    }

    private void thenListContainersIsNotCalled() throws DockerAccessException {
        Mockito.verify(queryService, Mockito.never())
            .listContainers( Mockito.anyBoolean());
    }
}
