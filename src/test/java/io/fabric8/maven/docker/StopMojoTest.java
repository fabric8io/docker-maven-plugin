package io.fabric8.maven.docker;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.print.Doc;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.ExecException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.model.Container;
import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;

public class StopMojoTest extends BaseMojoTest {
    @Tested(fullyInitialized = false)
    private StopMojo stopMojo;

    @Mocked
    private Container runningInstance;

    /**
     * Mock project with no images, no containers are stopped.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithNoImages() throws IOException, MojoExecutionException, ExecException {
        givenMavenProject();

        whenMojoExecutes();

        thenNoContainerLookupByImageOccurs();
        thenNoContainerIsStopped();
    }

    /**
     * Mock project with no images, but stopNamePattern is set, so containers are checked.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithStopNamePatternButNoImages() throws IOException, MojoExecutionException, ExecException {
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
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithStopNamePatternThatIsActuallyEmpty() throws IOException, MojoExecutionException, ExecException {
        givenMavenProject();
        givenStopNamePattern(" , , ");

        whenMojoExecutes();

        thenNoContainerLookupByImageOccurs();
        thenListContainersIsNotCalled();
        thenNoContainerIsStopped();
    }

    /**
     * Mock project with no images, but stopNamePattern is set, so a container is stopped.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithMatchingStopNamePatternButNoImages() throws IOException, MojoExecutionException, ExecException {
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
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithSingleImageNotLabelled() throws IOException, MojoExecutionException, ExecException {
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
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithSingleImageIsLabelled() throws IOException, MojoExecutionException, ExecException {
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
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithSingleImageIsLabelledRemoveVolume() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        givenContainerIsRunningForImage("example:latest", "container-id", "example-1");
        givenContainerHasGavLabels();

        Deencapsulation.setField(stopMojo, "removeVolumes", true);

        whenMojoExecutes();

        thenContainerLookupByImageOccurs("example:latest");
        thenListContainersIsNotCalled();
        thenContainerIsStopped("container-id", false, true);
    }

    /**
     * Mock project with one image, query service indicates running image, and it is labelled.
     * Removal should pass true for keepContainers, and query should exclude stopped containers.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithSingleImageIsLabelledKeepingContainers() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        givenContainerIsRunningForImage("example:latest", "container-id", "example-1");
        givenContainerHasGavLabels();

        Deencapsulation.setField(stopMojo, "keepContainer", true);

        whenMojoExecutes();

        thenContainerLookupByImageOccurs("example:latest");
        thenListContainersIsNotCalled();
        thenContainerIsStopped("container-id", true, false);
    }

    /**
     * Mock project with one image, query service indicates running image, which is not labelled,
     * but allContainers is true.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithSingleImageNotLabelledButAllContainersTrue() throws IOException, MojoExecutionException, ExecException {
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
     *
     * The containers are named with ascending indices - only the last should be stopped.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithMultipleImagesCheckLast(@Mocked Container running1, @Mocked Container running2, @Mocked Container running3) throws IOException, MojoExecutionException, ExecException {
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
     *
     * The containers are named with ascending indices, but there's a gap, so the last before
     * the gap and all the ones after are stopped.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithMultipleImagesIndexPatternMissingIndices(@Mocked Container running1, @Mocked Container running2, @Mocked Container running3) throws IOException, MojoExecutionException, ExecException {
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
     *
     * The containers are named with ascending indices - but this doesn't match the container
     * name pattern, so all are stopped.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithMultipleImagesNoIndexNamePattern(@Mocked Container running1, @Mocked Container running2, @Mocked Container running3) throws IOException, MojoExecutionException, ExecException {
        this.consoleLogger.setThreshold(0);
        givenProjectWithResolvedImage(singleImageWithBuild());

        givenAllContainersIsTrue();

        givenRunningContainer(running1, "container-id-1", "example-1");
        givenRunningContainer(running2, "container-id-2", "example-2");
        givenRunningContainer(running3, "container-id-3", "example-3");
        givenContainersAreRunningForImage("example:latest", running1, running2, running3);

        // If name pattern doesn't contain index placeholder, all containers are stopped
        Deencapsulation.setField(stopMojo, "containerNamePattern", "%n");

        whenMojoExecutes();

        thenContainerLookupByImageOccurs("example:latest");
        thenListContainersIsNotCalled();
        thenContainerIsStopped("container-id-1", false, false);
        thenContainerIsStopped("container-id-2", false, false);
        thenContainerIsStopped("container-id-3", false, false);
    }

    /**
     * Mock project with one image, query service indicates running image, but it is not labelled.
     * The stopNamePattern is set and does not match.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithSingleImageAndPatternDoesNotMatch() throws IOException, MojoExecutionException, ExecException {
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
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithSingleImageAndNoApplicablePattern() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuildAndStopNamePattern(" , , "));

        whenMojoExecutes();

        thenNoContainerLookupByImageOccurs();
        thenListContainersIsNotCalled();
        thenNoContainerIsStopped();
    }

    /**
     * Mock project with one image, query service indicates running image, but it is not labelled.
     * The stopNamePattern is set for images only and does not match.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test(expected = MojoExecutionException.class)
    public void stopWithSingleImageAndImagePatternSyntaxException() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuildAndStopNamePattern(" , image=%regex[surprise! [], "));

        whenMojoExecutes();

        thenNoContainerLookupByImageOccurs();
        thenListContainersIsNotCalled();
        thenNoContainerIsStopped();
    }

    /**
     * Mock project with one image, query service indicates running image, but it is not labelled.
     * The stopNamePattern is set for container names only and does not match.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test(expected = MojoExecutionException.class)
    public void stopWithSingleImageAndContainerNamePatternSyntaxException()
            throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuildAndStopNamePattern(" ,name= %regex[surprise! [], "));

        whenMojoExecutes();

        thenNoContainerLookupByImageOccurs();
        thenListContainersIsNotCalled();
        thenNoContainerIsStopped();
    }

    /**
     * Mock project with one image, query service indicates running image, but it is not labelled.
     * The stopNamePattern is set for images only and does not match.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithSingleImageAndImageOnlyPattern() throws IOException, MojoExecutionException, ExecException {
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
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithSingleImageAndNameOnlyPattern() throws IOException, MojoExecutionException, ExecException {
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
     *
     * The containers are named with ascending indices, but pattern matching is in effect, so
     * they are all stopped.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithMultipleImagesImageNamePattern(@Mocked Container running1, @Mocked Container running2, @Mocked Container running3) throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuildAndStopNamePattern("example:*"));

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
     *
     * The containers are named with ascending indices, but pattern matching is in effect, so
     * they are all stopped.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithMultipleImagesContainerNamePattern(@Mocked Container running1, @Mocked Container running2, @Mocked Container running3) throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuildAndStopNamePattern("example-*"));

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
     *
     * The containers are named with ascending indices, but pattern matching is in effect, so
     * they are all stopped.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithMultipleImagesContainerAndImageNamePattern(@Mocked Container running1, @Mocked Container running2, @Mocked Container running3) throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuildAndStopNamePattern("name=%regex[example-[14]],image=*:v2,**:v3"));

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
     *
     * The containers are named with ascending indices, but pattern matching is in effect, so
     * two of them are stopped.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void stopWithMultipleImagesContainerNameRegexPattern(@Mocked Container running1, @Mocked Container running2, @Mocked Container running3) throws IOException, MojoExecutionException, ExecException {
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

    private void givenProjectWithResolvedImages(List<ImageConfiguration> resolvedImages) {
        givenMavenProject(stopMojo);
        givenResolvedImages(stopMojo, resolvedImages);
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
        Deencapsulation.setField(stopMojo, "stopNamePattern", stopNamePattern);
    }

    private void givenAllContainersIsTrue() {
        Deencapsulation.setField(stopMojo, "allContainers", true);
    }

    private void givenRunningContainer(String containerId, String containerName, String imageName) {
        givenRunningContainer(this.runningInstance, containerId, containerName, imageName);
    }

    private void givenRunningContainer(Container instance, String containerId, String containerName) {
        givenRunningContainer(instance, containerId, containerName, null);
    }

    private void givenRunningContainer(Container instance, String containerId, String containerName, String imageName) {
        new Expectations() {{
            instance.getId(); result = containerId; minTimes = 0;
            instance.getName(); result = containerName; minTimes = 0;
            instance.getImage(); result = imageName; minTimes = 0;
        }};
    }

    private void givenListOfRunningContainers(Container... instances) throws DockerAccessException {
        new Expectations() {{
            queryService.listContainers(anyBoolean);
            result = (instances.length == 0 ? Collections.singletonList(runningInstance) : Arrays.asList(instances));
            minTimes = 0;
        }};
    }

    private void givenContainerIsRunningForImage(String imageName, String containerId, String containerName) throws DockerAccessException {
        givenRunningContainer(this.runningInstance, containerId, containerName, imageName);
        givenContainersAreRunningForImage(imageName, this.runningInstance);
    }

    private void givenContainersAreRunningForImage(String imageName, Container... containers) throws DockerAccessException {
        new Expectations() {{
            queryService.getContainersForImage(imageName, anyBoolean);
            result = Arrays.asList(containers);
            minTimes = 0;
        }};
    }

    private void givenContainerHasGavLabels() {
        givenContainerHasGavLabels(this.runningInstance);
    }

    private void givenContainerHasGavLabels(Container instance) {
        new Expectations() {{
            instance.getLabels();
            result = Collections.singletonMap(projectGavLabel.getKey(), projectGavLabel.getValue());
            minTimes = 0;
        }};
    }

    private void whenMojoExecutes() throws ExecException, IOException, MojoExecutionException {
        stopMojo.executeInternal(serviceHub);
    }

    private void thenNoContainerIsStopped() throws ExecException, DockerAccessException {
        new Verifications() {{
            runService.stopContainer(anyString, (ImageConfiguration)any, anyBoolean, anyBoolean);
            times = 0;
        }};
    }

    private void thenContainerIsStopped(String containerId, boolean keepContainer, boolean removeVolumes) throws ExecException, DockerAccessException {
        new Verifications() {{
            runService.stopContainer(containerId, (ImageConfiguration)any, keepContainer, removeVolumes);
        }};
    }

    private void thenContainerIsNotStopped(String containerId) throws ExecException, DockerAccessException {
        new Verifications() {{
            runService.stopContainer(containerId, (ImageConfiguration)any, anyBoolean, anyBoolean);
            times = 0;
        }};
    }

    private void thenNoContainerLookupByImageOccurs() throws DockerAccessException {
        new Verifications() {{
            queryService.getContainersForImage(anyString, anyBoolean);
            times = 0;
        }};
    }

    private void thenContainerLookupByImageOccurs(String imageName) throws DockerAccessException {
        new Verifications() {{
            queryService.getContainersForImage(imageName, anyBoolean);
            minTimes = 1;
        }};
    }

    private void thenListContainersIsCalled() throws DockerAccessException {
        new Verifications() {{
            queryService.listContainers(anyBoolean);
            minTimes = 1;
        }};
    }

    private void thenListContainersIsNotCalled() throws DockerAccessException {
        new Verifications() {{
            queryService.listContainers(anyBoolean);
            times = 0;
        }};
    }
}
