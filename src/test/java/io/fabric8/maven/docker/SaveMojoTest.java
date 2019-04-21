package io.fabric8.maven.docker;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.junit.Test;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ArchiveCompression;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.QueryService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.Logger;
import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Tested;

public class SaveMojoTest {

    @Injectable
    Logger log;

    @Tested(fullyInitialized = false)
    private SaveMojo saveMojo;

    @Mocked
    ServiceHub serviceHub;

    @Mocked
    QueryService queryService;

    @Mocked
    DockerAccess dockerAccess;

    @Mocked
    MavenProject mavenProject;

    @Mocked
    Build mavenBuild;

    @Mocked
    MavenProjectHelper mavenProjectHelper;

    @Test
    public void saveWithoutNameAliasOrFile() throws DockerAccessException, MojoExecutionException {
        ImageConfiguration image = new ImageConfiguration.Builder()
                .name("example:latest")
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("scratch")
                        .build())
                .build();

        new Expectations() {{
            mavenProject.getProperties(); result = new Properties();
            queryService.hasImage("example:latest"); result = true;
            dockerAccess.saveImage("example:latest", anyString, ArchiveCompression.gzip);
            serviceHub.getQueryService(); result = queryService;
            serviceHub.getDockerAccess(); result = dockerAccess;
        }};

        Deencapsulation.setField(saveMojo, "images", Collections.singletonList(image));
        Deencapsulation.setField(saveMojo, "resolvedImages", Collections.singletonList(image));
        Deencapsulation.setField(saveMojo, "project", mavenProject);
        Deencapsulation.setField(saveMojo, "projectHelper", mavenProjectHelper);

        saveMojo.executeInternal(serviceHub);
    }

    @Test
    public void saveAndAttachWithoutNameAliasOrFile() throws DockerAccessException, MojoExecutionException {
        ImageConfiguration image = new ImageConfiguration.Builder()
                .name("example:latest")
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("scratch")
                        .build())
                .build();

        new Expectations() {{
            mavenProject.getProperties(); result = new Properties();
            mavenProject.getBuild(); result = mavenBuild;
            mavenBuild.getDirectory(); result = "mock-target";
            queryService.hasImage("example:latest"); result = true;
            dockerAccess.saveImage("example:latest", anyString, ArchiveCompression.gzip);
            serviceHub.getQueryService(); result = queryService;
            serviceHub.getDockerAccess(); result = dockerAccess;
            mavenProjectHelper.attachArtifact(mavenProject, "tar.gz", "archive", new File("mock-target/example-latest.tar.gz"));
        }};

        Deencapsulation.setField(saveMojo, "images", Collections.singletonList(image));
        Deencapsulation.setField(saveMojo, "resolvedImages", Collections.singletonList(image));
        Deencapsulation.setField(saveMojo, "saveClassifier", "archive");
        Deencapsulation.setField(saveMojo, "project", mavenProject);
        Deencapsulation.setField(saveMojo, "projectHelper", mavenProjectHelper);

        saveMojo.executeInternal(serviceHub);
    }

    @Test
    public void saveWithFile() throws DockerAccessException, MojoExecutionException {
        ImageConfiguration image = new ImageConfiguration.Builder()
                .name("example:latest")
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("scratch")
                        .build())
                .build();

        new Expectations() {{
            mavenProject.getProperties(); result = new Properties();
            queryService.hasImage("example:latest"); result = true;
            dockerAccess.saveImage("example:latest", "destination/archive-name.tar.bz2", ArchiveCompression.bzip2);
            serviceHub.getQueryService(); result = queryService;
            serviceHub.getDockerAccess(); result = dockerAccess;
        }};

        Deencapsulation.setField(saveMojo, "images", Collections.singletonList(image));
        Deencapsulation.setField(saveMojo, "resolvedImages", Collections.singletonList(image));
        Deencapsulation.setField(saveMojo, "saveFile", "destination/archive-name.tar.bz2");
        Deencapsulation.setField(saveMojo, "project", mavenProject);
        Deencapsulation.setField(saveMojo, "projectHelper", mavenProjectHelper);

        saveMojo.executeInternal(serviceHub);
    }

    @Test
    public void saveAndAttachWithFile() throws DockerAccessException, MojoExecutionException {
        ImageConfiguration image = new ImageConfiguration.Builder()
                .name("example:latest")
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("scratch")
                        .build())
                .build();

        new Expectations() {{
            mavenProject.getProperties(); result = new Properties();
            queryService.hasImage("example:latest"); result = true;
            dockerAccess.saveImage("example:latest", "destination/archive-name.tar.bz2", ArchiveCompression.bzip2);
            serviceHub.getQueryService(); result = queryService;
            serviceHub.getDockerAccess(); result = dockerAccess;
            mavenProjectHelper.attachArtifact(mavenProject, "tar.bz", "archive", new File("destination/archive-name.tar.bz2"));
        }};

        Deencapsulation.setField(saveMojo, "images", Collections.singletonList(image));
        Deencapsulation.setField(saveMojo, "resolvedImages", Collections.singletonList(image));
        Deencapsulation.setField(saveMojo, "saveFile", "destination/archive-name.tar.bz2");
        Deencapsulation.setField(saveMojo, "saveClassifier", "archive");
        Deencapsulation.setField(saveMojo, "project", mavenProject);
        Deencapsulation.setField(saveMojo, "projectHelper", mavenProjectHelper);

        saveMojo.executeInternal(serviceHub);
    }

    @Test
    public void saveWithAlias() throws DockerAccessException, MojoExecutionException {
        ImageConfiguration image1 = new ImageConfiguration.Builder()
                .name("example1:latest")
                .alias("example1")
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("scratch")
                        .build())
                .build();

        ImageConfiguration image2 = new ImageConfiguration.Builder()
                .name("example2:latest")
                .alias("example2")
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("scratch")
                        .build())
                .build();

        new Expectations() {{
            mavenProject.getProperties(); result = new Properties();
            mavenProject.getBuild(); result = mavenBuild;
            mavenProject.getVersion(); result = "1.2.3-SNAPSHOT";
            mavenBuild.getDirectory(); result = "mock-target";
            queryService.hasImage("example2:latest"); result = true;
            dockerAccess.saveImage("example2:latest", "mock-target/example2-1.2.3-SNAPSHOT.tar.gz", ArchiveCompression.gzip);
            serviceHub.getQueryService(); result = queryService;
            serviceHub.getDockerAccess(); result = dockerAccess;
        }};

        Deencapsulation.setField(saveMojo, "images", Arrays.asList(image1, image2));
        Deencapsulation.setField(saveMojo, "resolvedImages", Arrays.asList(image1, image2));
        Deencapsulation.setField(saveMojo, "saveAlias", "example2");
        Deencapsulation.setField(saveMojo, "project", mavenProject);
        Deencapsulation.setField(saveMojo, "projectHelper", mavenProjectHelper);

        saveMojo.executeInternal(serviceHub);
    }

    @Test
    public void saveAndAttachWithAlias() throws DockerAccessException, MojoExecutionException {
        ImageConfiguration image1 = new ImageConfiguration.Builder()
                .name("example1:latest")
                .alias("example1")
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("scratch")
                        .build())
                .build();

        ImageConfiguration image2 = new ImageConfiguration.Builder()
                .name("example2:latest")
                .alias("example2")
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("scratch")
                        .build())
                .build();

        new Expectations() {{
            mavenProject.getProperties(); result = new Properties();
            mavenProject.getBuild(); result = mavenBuild;
            mavenProject.getVersion(); result = "1.2.3-SNAPSHOT";
            mavenBuild.getDirectory(); result = "mock-target";
            queryService.hasImage("example2:latest"); result = true;
            dockerAccess.saveImage("example2:latest", "mock-target/example2-1.2.3-SNAPSHOT.tar.gz", ArchiveCompression.gzip);
            serviceHub.getQueryService(); result = queryService;
            serviceHub.getDockerAccess(); result = dockerAccess;
            mavenProjectHelper.attachArtifact(mavenProject, "tar.gz", "archive-example2", new File("mock-target/example2-1.2.3-SNAPSHOT.tar.gz"));
        }};

        Deencapsulation.setField(saveMojo, "images", Arrays.asList(image1, image2));
        Deencapsulation.setField(saveMojo, "resolvedImages", Arrays.asList(image1, image2));
        Deencapsulation.setField(saveMojo, "saveAlias", "example2");
        Deencapsulation.setField(saveMojo, "saveClassifier", "archive-%a");
        Deencapsulation.setField(saveMojo, "project", mavenProject);
        Deencapsulation.setField(saveMojo, "projectHelper", mavenProjectHelper);

        saveMojo.executeInternal(serviceHub);
    }

    @Test
    public void saveAndAttachWithAliasButAlsoClassifier() throws DockerAccessException, MojoExecutionException {
        ImageConfiguration image1 = new ImageConfiguration.Builder()
                .name("example1:latest")
                .alias("example1")
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("scratch")
                        .build())
                .build();

        ImageConfiguration image2 = new ImageConfiguration.Builder()
                .name("example2:latest")
                .alias("example2")
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("scratch")
                        .build())
                .build();

        new Expectations() {{
            mavenProject.getProperties(); result = new Properties();
            mavenProject.getBuild(); result = mavenBuild;
            mavenProject.getVersion(); result = "1.2.3-SNAPSHOT";
            mavenBuild.getDirectory(); result = "mock-target";
            queryService.hasImage("example2:latest"); result = true;
            dockerAccess.saveImage("example2:latest", "mock-target/example2-1.2.3-SNAPSHOT.tar.gz", ArchiveCompression.gzip);
            serviceHub.getQueryService(); result = queryService;
            serviceHub.getDockerAccess(); result = dockerAccess;
            mavenProjectHelper.attachArtifact(mavenProject, "tar.gz", "preferred", new File("mock-target/example2-1.2.3-SNAPSHOT.tar.gz"));
        }};

        Deencapsulation.setField(saveMojo, "images", Arrays.asList(image1, image2));
        Deencapsulation.setField(saveMojo, "resolvedImages", Arrays.asList(image1, image2));
        Deencapsulation.setField(saveMojo, "saveAlias", "example2");
        Deencapsulation.setField(saveMojo, "saveClassifier", "preferred");
        Deencapsulation.setField(saveMojo, "project", mavenProject);
        Deencapsulation.setField(saveMojo, "projectHelper", mavenProjectHelper);

        saveMojo.executeInternal(serviceHub);
    }

    @Test
    public void noFailureWithEmptyImageList() throws DockerAccessException, MojoExecutionException {
        Deencapsulation.setField(saveMojo, "images", Collections.<ImageConfiguration>emptyList());
        Deencapsulation.setField(saveMojo, "resolvedImages", Collections.<ImageConfiguration>emptyList());

        saveMojo.executeInternal(serviceHub);
    }

    @Test
    public void noFailureWithEmptyBuildImageList() throws DockerAccessException, MojoExecutionException {
        ImageConfiguration image = new ImageConfiguration.Builder()
                .name("example:latest")
                .build();
        Deencapsulation.setField(saveMojo, "images", Collections.singletonList(image));
        Deencapsulation.setField(saveMojo, "resolvedImages", Collections.singletonList(image));

        saveMojo.executeInternal(serviceHub);
    }

    @Test(expected = MojoExecutionException.class)
    public void failureWithMultipleBuildImageList() throws DockerAccessException, MojoExecutionException {
        ImageConfiguration image1 = new ImageConfiguration.Builder()
                .name("example1:latest")
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("scratch")
                        .build())
                .build();
        ImageConfiguration image2 = new ImageConfiguration.Builder()
                .name("example2:latest")
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("scratch")
                        .build())
                .build();

        List<ImageConfiguration> images = Arrays.asList(image1, image2);
        Deencapsulation.setField(saveMojo, "images", images);
        Deencapsulation.setField(saveMojo, "resolvedImages", images);

        saveMojo.executeInternal(serviceHub);
    }

    @Test(expected = MojoExecutionException.class)
    public void failureWithSaveAliasAndName() throws DockerAccessException, MojoExecutionException {
        Deencapsulation.setField(saveMojo, "saveAlias", "not-null");
        Deencapsulation.setField(saveMojo, "saveName", "not-null");
        Deencapsulation.setField(saveMojo, "images", Collections.singletonList(new ImageConfiguration()));
        Deencapsulation.setField(saveMojo, "resolvedImages", Collections.singletonList(new ImageConfiguration()));

        saveMojo.executeInternal(serviceHub);
    }
}
