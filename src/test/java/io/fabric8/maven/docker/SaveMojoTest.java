package io.fabric8.maven.docker;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ArchiveCompression;
import io.fabric8.maven.docker.config.ImageConfiguration;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class SaveMojoTest extends MojoTestBase {
    @InjectMocks
    private SaveMojo saveMojo;

    @Test
    void saveWithoutNameAliasOrFile() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());
        givenQueryServiceHasImage("example:latest");

        whenMojoExecutes();

        thenImageIsSaved("example:latest", "mock-target/example-latest.tar.gz", ArchiveCompression.gzip);
    }

    @Test
    void saveWithoutNameAliasOrFileSkipped() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        saveMojo.skipSave = true;

        whenMojoExecutes();

        thenHasImageNotCalled();
        thenNoImageIsSaved();
    }

    @Test
    void saveMissingWithoutNameAliasOrFile() throws DockerAccessException {
        givenProjectWithResolvedImage(singleImageWithBuild());
        givenQueryServiceDoesNotHaveImage();

        Assertions.assertThrows(MojoExecutionException.class, this::whenMojoExecutes);

        thenNoImageIsSaved();
    }

    @Test
    void saveAndAttachWithoutNameAliasOrFile() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());
        givenQueryServiceHasImage("example:latest");

        saveMojo.saveClassifier = "archive";

        whenMojoExecutes();

        thenImageIsSaved("example:latest", "mock-target/example-latest.tar.gz", ArchiveCompression.gzip);
        thenArtifactAttached("tar.gz", "archive", "mock-target/example-latest.tar.gz");
    }

    @Test
    void saveWithFile() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());
        givenQueryServiceHasImage("example:latest");

        saveMojo.saveFile = "destination/archive-name.tar.bz2";

        whenMojoExecutes();

        thenImageIsSaved("example:latest", "destination/archive-name.tar.bz2", ArchiveCompression.bzip2);
    }

    @Test
    void saveWithFileInSystemProperty() throws DockerAccessException, MojoExecutionException {
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
    void saveAndAttachWithFile() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());
        givenQueryServiceHasImage("example:latest");

        saveMojo.saveFile = "destination/archive-name.tar.bz2";
        saveMojo.saveClassifier = "archive";

        whenMojoExecutes();

        thenImageIsSaved("example:latest", "destination/archive-name.tar.bz2", ArchiveCompression.bzip2);
        thenArtifactAttached("tar.bz", "archive", "destination/archive-name.tar.bz2");
    }

    @Test
    void saveWithAlias() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImages(twoImagesWithBuild());
        givenQueryServiceHasImage("example2:latest");

        saveMojo.saveAlias = "example2";

        whenMojoExecutes();

        thenImageIsSaved("example2:latest", "mock-target/example2-1.0.0-MOCK.tar.gz", ArchiveCompression.gzip);
    }

    @Test
    void saveWithNonExistentAlias() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithBuild());

        saveMojo.saveAlias = "example3";

        Assertions.assertThrows(MojoExecutionException.class, this::whenMojoExecutes);

        thenHasImageNotCalled();
        thenNoImageIsSaved();
    }

    @Test
    void saveAndAttachWithAlias() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImages(twoImagesWithBuild());
        givenQueryServiceHasImage("example2:latest");

        saveMojo.saveAlias = "example2";
        saveMojo.saveClassifier = "archive-%a";

        whenMojoExecutes();

        thenImageIsSaved("example2:latest", "mock-target/example2-1.0.0-MOCK.tar.gz", ArchiveCompression.gzip);
        thenArtifactAttached("tar.gz", "archive-example2", "mock-target/example2-1.0.0-MOCK.tar.gz");
    }

    @Test
    void saveAndAttachWithAliasButAlsoClassifier() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImages(twoImagesWithBuild());
        givenQueryServiceHasImage("example2:latest");

        saveMojo.saveAlias = "example2";
        saveMojo.saveClassifier = "preferred";

        whenMojoExecutes();

        thenImageIsSaved("example2:latest", "mock-target/example2-1.0.0-MOCK.tar.gz", ArchiveCompression.gzip);
        thenArtifactAttached("tar.gz", "preferred", "mock-target/example2-1.0.0-MOCK.tar.gz");
    }

    @Test
    void noFailureWithEmptyImageList() throws DockerAccessException, MojoExecutionException {
        saveMojo.images = Collections.emptyList();
        saveMojo.resolvedImages = Collections.emptyList();

        whenMojoExecutes();
        // no action from mojo
        Mockito.verifyNoInteractions(dockerAccess);
    }

    @Test
    void noFailureWithEmptyBuildImageList() throws DockerAccessException, MojoExecutionException {
        givenProjectWithResolvedImage(singleImageWithoutBuildOrRun());

        whenMojoExecutes();
        // no failure
        Mockito.verifyNoInteractions(dockerAccess);
    }

    @Test
    void failureWithMultipleBuildImageList() {
        givenProjectWithResolvedImages(twoImagesWithBuild());

        Assertions.assertThrows(MojoExecutionException.class, this::whenMojoExecutes);
    }

    @Test
    void failureWithSaveAliasAndName() {
        givenProjectWithResolvedImage(singleImageWithBuild());

        saveMojo.saveAlias = "not-null";
        saveMojo.saveName = "not-null";

        Assertions.assertThrows(MojoExecutionException.class, this::whenMojoExecutes);
        // fails
    }

    @Override
    protected void givenMavenProject(AbstractDockerMojo mojo) {
        super.givenMavenProject(mojo);
        ((SaveMojo) mojo).projectHelper = mavenProjectHelper;
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
        Mockito.doReturn(true).when(queryService).hasImage(name);
    }

    private void givenQueryServiceDoesNotHaveImage() throws DockerAccessException {
        Mockito.doReturn(false).when(queryService).hasImage(Mockito.anyString());
    }

    private void thenHasImageNotCalled() throws DockerAccessException {
        Mockito.verify(queryService, Mockito.times(0)).hasImage(Mockito.anyString());
    }

    private void thenNoImageIsSaved() throws DockerAccessException {
        Mockito.verify(dockerAccess, Mockito.never()).saveImage(Mockito.anyString(), Mockito.anyString(), Mockito.any());
    }

    private void thenImageIsSaved(String name, String fileName, ArchiveCompression compression) throws DockerAccessException {
        ArgumentCaptor<String> savedImage = ArgumentCaptor.forClass(String.class);
        Mockito.verify(dockerAccess).saveImage(Mockito.eq(name), savedImage.capture(), Mockito.eq(compression));
        assertAbsolutePathEquals(resolveMavenProjectPath(fileName), resolveMavenProjectPath(savedImage.getValue()));
    }

    private void thenArtifactAttached(String type, String classifier, String fileName) {
        ArgumentCaptor<File> artifactCapture = ArgumentCaptor.forClass(File.class);
        Mockito.verify(mavenProjectHelper)
            .attachArtifact(Mockito.eq(mavenProject), Mockito.eq(type), Mockito.eq(classifier), artifactCapture.capture());
        assertAbsolutePathEquals(resolveMavenProjectPath(fileName), artifactCapture.getValue());
    }
}
