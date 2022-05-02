package io.fabric8.maven.docker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.model.Image;
import io.fabric8.maven.docker.model.ImageDetails;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class RemoveMojoTest extends MojoTestBase {
    @InjectMocks
    private RemoveMojo removeMojo;

    /**
     * Mock project with no images, no images are removed.
     */
    @Test
    void removeWithNoImages() throws IOException, MojoExecutionException {
        givenMavenProject();
        
        whenMojoExecutes();

        thenHasImageIsNotCalled();
        thenListImagesIsNotCalled();
        thenRemoveImageIsNotCalled();
    }

    /**
     * Mock project with no images, but a pattern is provided.
     */
    @Test
    void removeWithRemovePatternAndNoImages() throws IOException, MojoExecutionException {
        givenMavenProject();
        givenRemoveNamePattern("example:*");
        givenListOfImageNames("example:latest");

        whenMojoExecutes();

        thenListImagesIsCalled();
        thenRemoveImageIsCalledFor("example:latest");
    }

    /**
     * Mock project with no images, and an empty set of patterns is provided.
     */
    @Test
    void removeWithEmptyRemovePatternsAndNoImages() throws IOException, MojoExecutionException {
        givenMavenProject();
        givenRemoveNamePattern(" , , ");
        givenListOfImageNames("example:latest");

        whenMojoExecutes();

        thenListImagesIsNotCalled();
        thenRemoveImageIsNotCalledFor("example:latest");
    }

    /**
     * Mock project with one image that is removed.
     */
    @ParameterizedTest
    @ValueSource(strings = {"all", "build", "data"})
    @NullSource
    void removeAllWithOneImage(String removeMode) throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());
        
        givenRemoveMode(removeMode);

        givenHasImage("example:latest");

        whenMojoExecutes();

        thenRemoveImageIsCalledFor("example:latest");
        thenListImagesIsNotCalled();
    }

    /**
     * Mock project with one image that is removed.
     */
    @Test
    void removeAllWithOneImageWithoutBuildOrRun() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithoutBuildOrRun());

        givenRemoveMode("all");

        givenHasImage("example:latest");

        whenMojoExecutes();

        thenRemoveImageIsCalledFor("example:latest");
        thenListImagesIsNotCalled();
    }

    /**
     * Mock project with one build image that is removed
     */
    @Test
    void removeNullWithOneRunImage() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithRun());

        givenRemoveMode(null);

        givenHasImage("example:latest");

        whenMojoExecutes();

        thenRemoveImageIsNotCalled();
        thenListImagesIsNotCalled();
    }

    /**
     * Mock project with one build image that is removed
     */
    @Test
    void removeNullWithRemoveAllWithOneBuildImage() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        givenRemoveMode(null);
        givenRemoveAll(true);

        givenHasImage("example:latest");

        whenMojoExecutes();

        thenRemoveImageIsCalledFor("example:latest");
        thenListImagesIsNotCalled();
    }

    /**
     * Mock project with one build image that is removed
     */
    @Test
    void removeNullWithRemoveAllFalseWithOneBuildImage() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        givenRemoveMode(null);
        givenRemoveAll(false);

        givenHasImage("example:latest");

        whenMojoExecutes();

        thenRemoveImageIsCalledFor("example:latest");
        thenListImagesIsNotCalled();
    }

    /**
     * Mock project with one build image that is removed
     */
    @Test
    void removeNullWithRemoveAllWithOneRunImage() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithRun());

        givenRemoveMode(null);
        givenRemoveAll(false);

        givenHasImage("example:latest");

        whenMojoExecutes();

        thenRemoveImageIsNotCalled();
        thenListImagesIsNotCalled();
    }

    /**
     * Mock project with one build image that is removed
     */
    @Test
    void removeRunWithOneRunOnlyImage() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithRun());

        givenRemoveMode("run");

        givenHasImage("example:latest");

        whenMojoExecutes();

        thenRemoveImageIsCalledFor("example:latest");
        thenListImagesIsNotCalled();
    }

    /**
     * Mock project with one build image that is removed
      */
    @Test
    void removeDataWithOneBuildAndRunImage() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuildAndRun());

        givenRemoveMode("data");

        whenMojoExecutes();

        thenHasImageIsNotCalled();
        thenRemoveImageIsNotCalledFor("example:latest");
        thenListImagesIsNotCalled();
    }

    /**
     * Mock project with one build image that is removed, as well as its tags
     */
    @Test
    void removeBuildWithOneBuildImageAndTags() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuildWithTags("fastest", "loosest"));

        givenRemoveMode("build");
        givenSkipTag(false);

        givenHasImage("example:latest");
        givenHasImage("example:fastest");
        givenHasImage("example:loosest");

        whenMojoExecutes();

        thenRemoveImageIsCalledFor("example:latest");
        thenRemoveImageIsCalledFor("example:fastest");
        thenRemoveImageIsCalledFor("example:loosest");
        thenListImagesIsNotCalled();
    }

    /**
     * Mock project with one build image that is removed, as well as its tags
     */
    @Test
    void removeBuildWithOneBuildImageAndTagsSkippingTags() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuildWithTags("fastest", "loosest"));

        givenRemoveMode("build");
        givenSkipTag(true);

        givenHasImage("example:latest");
        givenHasImage("example:fastest");
        givenHasImage("example:loosest");

        whenMojoExecutes();

        thenRemoveImageIsCalledFor("example:latest");
        thenRemoveImageIsNotCalledFor("example:fastest");
        thenRemoveImageIsNotCalledFor("example:loosest");
        thenListImagesIsNotCalled();
    }

    /**
     * Mock project with no build images, no images are removed.
     */
    @Test
    void removeBuildWithNoBuildImage() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithoutBuildOrRun());

        givenRemoveMode("build");

        whenMojoExecutes();

        thenHasImageIsNotCalled();
        thenListImagesIsNotCalled();
        thenRemoveImageIsNotCalled();
    }

    /**
     * Mock project with one image that is removed by pattern
     */
    @Test
    void removeAllWithOneImageAndPattern() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithoutBuildOrRunWithRemoveNamePattern("example:*"));

        givenRemoveMode("all");

        givenListOfImageNames("example:latest");

        whenMojoExecutes();

        thenListImagesIsCalled();
        thenRemoveImageIsCalledFor("example:latest");
    }

    /**
     * Mock project with one image that is removed by pattern
     */
    @Test
    void removeAllWithOneImageAndImagePatternOnly() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithoutBuildOrRunWithRemoveNamePattern("**:latest"));

        givenRemoveMode("all");
        givenRemoveNamePattern(null);

        givenListOfImageNames("example:latest", "example:fastest", "example:loosest");

        whenMojoExecutes();

        thenListImagesIsCalled();
        thenRemoveImageIsCalledFor("example:latest");
        thenRemoveImageIsNotCalledFor("example:fastest");
        thenRemoveImageIsNotCalledFor("example:loosest");
    }

    /**
     * Mock project with one image that is removed by pattern
     */
    @Test
    void removeAllWithOneImageAndUnspecifiedTags() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithoutBuildOrRunWithRemoveNamePattern("example:*"));

        givenRemoveMode("all");
        givenRemoveNamePattern(null);

        givenListOfImageNames("example:latest", "example:earlier-run", "example:1.0.0-MOCK");

        whenMojoExecutes();

        thenListImagesIsCalled();
        thenRemoveImageIsCalledFor("example:latest");
        thenRemoveImageIsCalledFor("example:earlier-run");
        thenRemoveImageIsCalledFor("example:1.0.0-MOCK");
    }

    /**
     * Mock project with one image that is removed by pattern
     */
    @Test
    void removeAllWithOneImageAndInvalidPattern() throws IOException {
        givenProjectWithResolvedImage(singleImageWithoutBuildOrRunWithRemoveNamePattern("%regex[[]"));

        givenRemoveMode("all");

        givenListOfImageNames("example:latest");

        Assertions.assertThrows(MojoExecutionException.class, this::whenMojoExecutes);

        thenListImagesIsNotCalled();
        thenRemoveImageIsNotCalledFor("example:latest");
    }

    /**
     * Mock project with one image that is removed by pattern
     */
    @Test
    void removeAllWithOneImageAndNoApplicablePattern() throws IOException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithoutBuildOrRunWithRemoveNamePattern("container=example:*"));

        givenRemoveMode("all");

        givenListOfImageNames("example:earlier-run");

        whenMojoExecutes();

        thenListImagesIsNotCalled();
        thenRemoveImageIsNotCalledFor("example:latest");
    }

    private void givenMavenProject() {
        givenMavenProject(removeMojo);
    }

    private void givenProjectWithResolvedImage(ImageConfiguration image) {
        givenMavenProject(removeMojo);
        givenResolvedImages(removeMojo, Collections.singletonList(image));
    }

    protected ImageConfiguration singleImageWithoutBuildOrRunWithRemoveNamePattern(String removeNamePattern) {
        return new ImageConfiguration.Builder()
                .name("example:latest")
                .removeNamePattern(removeNamePattern)
                .build();
    }

    private void givenRemoveMode(String removeMode) {
        removeMojo.removeMode= removeMode;
    }

    @SuppressWarnings("deprecation")
    private void givenRemoveAll(boolean removeAll) {
        removeMojo.removeAll= removeAll;
    }

    private void givenRemoveNamePattern(String removeNamePattern) {
        removeMojo.removeNamePattern= removeNamePattern;
    }

    private void givenSkipTag(boolean skipTag) {
        removeMojo.skipTag= skipTag;
    }

    private void givenHasImage(String imageName) throws DockerAccessException {
        Mockito.lenient().doReturn(true).when(queryService).hasImage(imageName);
    }

    private void givenListOfImageNames(String... imageNames) throws DockerAccessException {
        final List<Image> images = new ArrayList<>();

        for(String imageName : imageNames) {
            JsonObject json = new JsonObject();
            json.addProperty(ImageDetails.ID, DigestUtils.sha256Hex(imageName));

            JsonArray repoTags = new JsonArray();
            repoTags.add(imageName);

            json.add(ImageDetails.REPO_TAGS, repoTags);

            images.add(new ImageDetails(json));
        }

        Mockito.lenient().doReturn(images).when(queryService).listImages(Mockito.anyBoolean());
        for(String imageName : imageNames) {
            Mockito.lenient().doReturn(true).when(queryService).hasImage(imageName);
            Mockito.lenient().doReturn(true).when(dockerAccess).removeImage(imageName, false);
            Mockito.lenient().doReturn(true).when(dockerAccess).removeImage(imageName, true);
        }
    }

    private void whenMojoExecutes() throws IOException, MojoExecutionException {
        removeMojo.executeInternal(serviceHub);
    }
    
    private void thenHasImageIsNotCalled() throws DockerAccessException {
        Mockito.verify(queryService, Mockito.times(0))
            .hasImage(Mockito.anyString());
    }
    
    private void thenListImagesIsCalled() throws DockerAccessException {
        verifyListImages(1);
    }

    private void thenListImagesIsNotCalled() throws DockerAccessException {
        verifyListImages(0);
    }

    private void verifyListImages(int wantedNumberOfInvocations) throws DockerAccessException {
        Mockito.verify(queryService, Mockito.times(wantedNumberOfInvocations))
            .listImages(Mockito.anyBoolean());
    }

    private void thenRemoveImageIsNotCalled() throws DockerAccessException {
        Mockito.verify(dockerAccess, Mockito.never())
            .removeImage(Mockito.anyString(), Mockito.anyBoolean());
    }

    private void thenRemoveImageIsNotCalledFor(String imageName) throws DockerAccessException {
        verifyRemoveImage(imageName, 0);
    }

    private void thenRemoveImageIsCalledFor(String imageName) throws DockerAccessException {
        verifyRemoveImage(imageName, 1);
    }

    private void verifyRemoveImage(String imageName, int wantedNumberOfInvocations) throws DockerAccessException {
        Mockito.verify(dockerAccess, Mockito.times(wantedNumberOfInvocations)).removeImage(imageName, true);
    }
}
