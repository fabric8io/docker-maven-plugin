package io.fabric8.maven.docker;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ArchiveCompression;
import io.fabric8.maven.docker.config.ImageConfiguration;
import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Tested;
import mockit.Verifications;

public class SaveMojoTest extends BaseMojoTest {
    @Tested(fullyInitialized = false)
    private SaveMojo saveMojo;

    @Test
    public void saveWithoutNameAliasOrFile() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());
        givenQueryServiceHasImage("example:latest");

        whenMojoExecutes();

        thenImageIsSaved("example:latest", "mock-target/example-latest.tar.gz", ArchiveCompression.gzip);
    }

    @Test
    public void saveWithoutNameAliasOrFileSkipped() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        Deencapsulation.setField(saveMojo, "skipSave", true);

        whenMojoExecutes();

        thenHasImageNotCalled();
        thenNoImageIsSaved();
    }

    @Test(expected = MojoExecutionException.class)
    public void saveMissingWithoutNameAliasOrFile() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());
        givenQueryServiceDoesNotHaveImage();

        whenMojoExecutes();

        thenNoImageIsSaved();
    }

    @Test
    public void saveAndAttachWithoutNameAliasOrFile() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());
        givenQueryServiceHasImage("example:latest");

        Deencapsulation.setField(saveMojo, "saveClassifier", "archive");

        whenMojoExecutes();

        thenImageIsSaved("example:latest", "mock-target/example-latest.tar.gz", ArchiveCompression.gzip);
        thenArtifactAttached("tar.gz", "archive", "mock-target/example-latest.tar.gz");
    }

    @Test
    public void saveWithFile() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());
        givenQueryServiceHasImage("example:latest");

        Deencapsulation.setField(saveMojo, "saveFile", "destination/archive-name.tar.bz2");

        whenMojoExecutes();

        thenImageIsSaved("example:latest", "destination/archive-name.tar.bz2", ArchiveCompression.bzip2);
    }

    @Test
    public void saveWithFileInSystemProperty() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());
        givenQueryServiceHasImage("example:latest");

        System.setProperty("docker.save.file", "destination/archive-name.tar.bz2");

        try {
            whenMojoExecutes();
        } finally {
            System.clearProperty("docker.save.file");
        }

        thenImageIsSaved("example:latest", "destination/archive-name.tar.bz2", ArchiveCompression.bzip2);
    }

    @Test
    public void saveAndAttachWithFile() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());
        givenQueryServiceHasImage("example:latest");

        Deencapsulation.setField(saveMojo, "saveFile", "destination/archive-name.tar.bz2");
        Deencapsulation.setField(saveMojo, "saveClassifier", "archive");

        whenMojoExecutes();

        thenImageIsSaved("example:latest", "destination/archive-name.tar.bz2", ArchiveCompression.bzip2);
        thenArtifactAttached("tar.bz", "archive", "destination/archive-name.tar.bz2");
    }

    @Test
    public void saveWithAlias() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImages(twoImagesWithBuild());
        givenQueryServiceHasImage("example2:latest");

        Deencapsulation.setField(saveMojo, "saveAlias", "example2");

        whenMojoExecutes();

        thenImageIsSaved("example2:latest", "mock-target/example2-1.0.0-MOCK.tar.gz", ArchiveCompression.gzip);
    }


    @Test(expected = MojoExecutionException.class)
    public void saveWithNonExistentAlias() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        Deencapsulation.setField(saveMojo, "saveAlias", "example3");

        whenMojoExecutes();

        thenHasImageNotCalled();
        thenNoImageIsSaved();
    }

    @Test
    public void saveAndAttachWithAlias() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImages(twoImagesWithBuild());
        givenQueryServiceHasImage("example2:latest");

        Deencapsulation.setField(saveMojo, "saveAlias", "example2");
        Deencapsulation.setField(saveMojo, "saveClassifier", "archive-%a");

        whenMojoExecutes();

        thenImageIsSaved("example2:latest", "mock-target/example2-1.0.0-MOCK.tar.gz", ArchiveCompression.gzip);
        thenArtifactAttached("tar.gz", "archive-example2", "mock-target/example2-1.0.0-MOCK.tar.gz");
    }

    @Test
    public void saveAndAttachWithAliasButAlsoClassifier() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImages(twoImagesWithBuild());
        givenQueryServiceHasImage("example2:latest");

        Deencapsulation.setField(saveMojo, "saveAlias", "example2");
        Deencapsulation.setField(saveMojo, "saveClassifier", "preferred");

        whenMojoExecutes();

        thenImageIsSaved("example2:latest", "mock-target/example2-1.0.0-MOCK.tar.gz", ArchiveCompression.gzip);
        thenArtifactAttached("tar.gz", "preferred", "mock-target/example2-1.0.0-MOCK.tar.gz");
    }

    @Test
    public void noFailureWithEmptyImageList() throws DockerAccessException, MojoExecutionException {
        Deencapsulation.setField(saveMojo, "images", Collections.<ImageConfiguration>emptyList());
        Deencapsulation.setField(saveMojo, "resolvedImages", Collections.<ImageConfiguration>emptyList());

        whenMojoExecutes();
        // no action from mojo
    }

    @Test
    public void noFailureWithEmptyBuildImageList() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithoutBuildOrRun());

        whenMojoExecutes();
        // no failure
    }

    @Test(expected = MojoExecutionException.class)
    public void failureWithMultipleBuildImageList() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImages(twoImagesWithBuild());

        whenMojoExecutes();
        // throws exception
    }

    @Test(expected = MojoExecutionException.class)
    public void failureWithSaveAliasAndName() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        Deencapsulation.setField(saveMojo, "saveAlias", "not-null");
        Deencapsulation.setField(saveMojo, "saveName", "not-null");

        whenMojoExecutes();
        // fails
    }

    @Override
    protected void givenMavenProject(AbstractDockerMojo mojo) {
        super.givenMavenProject(mojo);

        Deencapsulation.setField(mojo, "projectHelper", mavenProjectHelper);
    }

    private void givenProjectWithResolvedImage(ImageConfiguration image) {
        givenMavenProject(saveMojo);
        givenResolvedImages(saveMojo, Collections.singletonList(image));
    }

    private void givenProjectWithResolvedImages(List<ImageConfiguration> resolvedImages) {
        givenMavenProject(saveMojo);
        givenResolvedImages(saveMojo, resolvedImages);
    }

    private void whenMojoExecutes() throws DockerAccessException, MojoExecutionException {
        saveMojo.executeInternal(serviceHub);
    }

    private void givenQueryServiceHasImage(final String name) throws DockerAccessException {
        new Expectations() {{
            queryService.hasImage(name); result = true;
        }};
    }

    private void givenQueryServiceDoesNotHaveImage() throws DockerAccessException {
        new Expectations() {{
            queryService.hasImage(anyString); result = false;
        }};
    }

    private void givenQueryServiceDoesNotHaveImage(final String name) throws DockerAccessException {
        new Expectations() {{
            queryService.hasImage(name); result = false;
        }};
    }

    private void thenHasImageNotCalled() throws DockerAccessException {
        new Verifications() {{
            queryService.hasImage(anyString); times = 0;
        }};
    }

    private void thenNoImageIsSaved() throws DockerAccessException {
        new Verifications() {{
            dockerAccess.saveImage(anyString, anyString, (ArchiveCompression)any); times = 0;
        }};
    }

    private void thenImageIsSaved(String name, String fileName, ArchiveCompression compression) throws DockerAccessException {
        new Verifications() {{
            dockerAccess.saveImage(name, fileName, compression);
        }};
    }

    private void thenArtifactAttached(String type, String classifier, String fileName) {
        new Verifications() {{
            mavenProjectHelper.attachArtifact(mavenProject, type, classifier, new File(fileName));
        }};
    }
}
