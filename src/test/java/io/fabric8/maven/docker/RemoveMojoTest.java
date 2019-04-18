package io.fabric8.maven.docker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.print.Doc;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Ignore;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.ExecException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.model.Image;
import io.fabric8.maven.docker.model.ImageDetails;
import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Tested;
import mockit.Verifications;

public class RemoveMojoTest extends BaseMojoTest {
    @Tested(fullyInitialized = false)
    private RemoveMojo removeMojo;

    /**
     * Mock project with no images, no images are removed.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void removeWithNoImages() throws IOException, MojoExecutionException, ExecException {
        givenMavenProject();
        
        whenMojoExecutes();

        thenHasImageIsNotCalled();
        thenListImagesIsNotCalled();
        thenRemoveImageIsNotCalled();
    }

    /**
     * Mock project with one image that is removed.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void removeAllWithOneImage() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuild());
        
        givenRemoveMode("all");

        givenHasImage("example:latest");

        whenMojoExecutes();

        thenRemoveImageIsCalledFor("example:latest");
        thenListImagesIsNotCalled();
    }

    /**
     * Mock project with one image that is removed.
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void removeAllWithOneImageWithoutBuildOrRun() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithoutBuildOrRun());

        givenRemoveMode("all");

        givenHasImage("example:latest");

        whenMojoExecutes();

        thenRemoveImageIsCalledFor("example:latest");
        thenListImagesIsNotCalled();
    }

    /**
     * Mock project with one build image that is removed
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void removeBuildWithOneBuildImage() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        givenRemoveMode("build");

        givenHasImage("example:latest");

        whenMojoExecutes();

        thenRemoveImageIsCalledFor("example:latest");
        thenListImagesIsNotCalled();
    }

    /**
     * Mock project with one build image that is removed
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void removeNullWithOneBuildImage() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        givenRemoveMode(null);

        givenHasImage("example:latest");

        whenMojoExecutes();

        thenRemoveImageIsCalledFor("example:latest");
        thenListImagesIsNotCalled();
    }

    /**
     * Mock project with one build image that is removed
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    @Ignore
    public void removeDataWithOneBuildOnlyImage() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        givenRemoveMode("data");

        givenHasImage("example:latest");

        whenMojoExecutes();

        thenRemoveImageIsCalledFor("example:latest");
        thenListImagesIsNotCalled();
    }

    /**
     * Mock project with one build image that is removed
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void removeDataWithOneBuildAndRunImage() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithBuildAndRun());

        givenRemoveMode("data");

        whenMojoExecutes();

        thenHasImageIsNotCalled();
        thenRemoveImageIsNotCalledFor("example:latest");
        thenListImagesIsNotCalled();
    }

    /**
     * Mock project with one build image that is removed, as well as its tags
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void removeBuildWithOneBuildImageAndTags() throws IOException, MojoExecutionException, ExecException {
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
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void removeBuildWithOneBuildImageAndTagsSkippingTags() throws IOException, MojoExecutionException, ExecException {
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
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void removeBuildWithNoBuildImage() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithoutBuildOrRun());

        givenRemoveMode("build");

        whenMojoExecutes();

        thenHasImageIsNotCalled();
        thenListImagesIsNotCalled();
        thenRemoveImageIsNotCalled();
    }

    /**
     * Mock project with one image that is removed by pattern
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void removeAllWithOneImageAndPattern() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithoutBuildOrRun());

        givenRemoveMode("all");
        givenRemoveNamePattern("example:*");

        givenListOfImageNames("example:latest");

        whenMojoExecutes();

        thenListImagesIsCalled();
        thenRemoveImageIsCalledFor("example:latest");
    }

    /**
     * Mock project with one image that is removed by pattern
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void removeAllWithOneImageAndOverridePattern() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(
                new ImageConfiguration.Builder()
                .name("example:latest")
                .removeNamePattern("**:latest")
                .build());

        givenRemoveMode("all");
        givenRemoveNamePattern("example:*");

        givenListOfImageNames("example:latest", "example:fastest", "example:loosest");

        whenMojoExecutes();

        thenListImagesIsCalled();
        thenRemoveImageIsCalledFor("example:latest");
    }

    /**
     * Mock project with one image that is removed by pattern
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void removeAllWithOneImageAndImagePatternOnly() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(
                new ImageConfiguration.Builder()
                .name("example:latest")
                .removeNamePattern("**:latest")
                .build());

        givenRemoveMode("all");
        givenRemoveNamePattern(null);

        givenListOfImageNames("example:latest", "example:fastest", "example:loosest");

        whenMojoExecutes();

        thenListImagesIsCalled();
        thenRemoveImageIsCalledFor("example:latest");
    }

    /**
     * Mock project with one image that is removed by pattern
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void removeAllWithOneImageAndUnspecifiedTags() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithoutBuildOrRun());

        givenRemoveMode("all");
        givenRemoveNamePattern("example:*");

        givenListOfImageNames("example:latest", "example:earlier-run", "example:1.0.0-MOCK");

        whenMojoExecutes();

        thenListImagesIsCalled();
        thenRemoveImageIsCalledFor("example:latest");
        thenRemoveImageIsCalledFor("example:earlier-run");
        thenRemoveImageIsCalledFor("example:1.0.0-MOCK");
    }

    /**
     * Mock project with one image that is removed by pattern
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test(expected = MojoExecutionException.class)
    public void removeAllWithOneImageAndInvalidPattern() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithoutBuildOrRun());

        givenRemoveMode("all");
        givenRemoveNamePattern("%regex[[]");

        givenListOfImageNames("example:latest");

        whenMojoExecutes();

        thenListImagesIsNotCalled();
        thenRemoveImageIsNotCalledFor("example:latest");
    }

    /**
     * Mock project with one image that is removed by pattern
     *
     * @throws IOException
     * @throws MojoExecutionException
     * @throws ExecException
     */
    @Test
    public void removeAllWithOneImageAndNoApplicablePattern() throws IOException, MojoExecutionException, ExecException {
        givenProjectWithResolvedImage(singleImageWithoutBuildOrRun());

        givenRemoveMode("all");
        givenRemoveNamePattern("container=example:*");

        givenListOfImageNames("example:latest");

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

    private void givenProjectWithResolvedImages(List<ImageConfiguration> resolvedImages) {
        givenMavenProject(removeMojo);
        givenResolvedImages(removeMojo, resolvedImages);
    }
    
    private void givenRemoveMode(String removeMode) {
        Deencapsulation.setField(removeMojo, "removeMode", removeMode);
    }

    private void givenRemoveNamePattern(String removeNamePattern) {
        Deencapsulation.setField(removeMojo, "removeNamePattern", removeNamePattern);
    }

    private void givenSkipTag(boolean skipTag) {
        Deencapsulation.setField(removeMojo, "skipTag", skipTag);
    }

    private void givenHasImage(String imageName) throws DockerAccessException {
        new Expectations() {{
            queryService.hasImage(imageName); result = true; minTimes = 0;
        }};
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

        new Expectations() {{
            queryService.listImages(anyBoolean); result = images; minTimes = 0;
            for(String imageName : imageNames) {
                queryService.hasImage(imageName); result = true; minTimes = 0;
                dockerAccess.removeImage(imageName, anyBoolean); result = true; minTimes = 0;
            }
        }};
    }

    private void whenMojoExecutes() throws ExecException, IOException, MojoExecutionException {
        removeMojo.executeInternal(serviceHub);
    }
    
    private void thenHasImageIsNotCalled() throws DockerAccessException {
        new Verifications() {{
            queryService.hasImage(anyString); times = 0;
        }};
    }
    
    private void thenListImagesIsCalled() throws DockerAccessException {
        new Verifications() {{
            queryService.listImages(anyBoolean);
        }};
    }

    private void thenListImagesIsNotCalled() throws DockerAccessException {
        new Verifications() {{
            queryService.listImages(anyBoolean); times = 0;
        }};
    }

    private void thenRemoveImageIsNotCalled() throws DockerAccessException {
        new Verifications() {{
            dockerAccess.removeImage(anyString, anyBoolean); times = 0;
        }};
    }

    private void thenRemoveImageIsNotCalledFor(String imageName) throws DockerAccessException {
        new Verifications() {{
            dockerAccess.removeImage(imageName, true); times = 0;
        }};
    }

    private void thenRemoveImageIsCalledFor(String imageName) throws DockerAccessException {
        new Verifications() {{
            dockerAccess.removeImage(imageName, true);
        }};
    }
}
