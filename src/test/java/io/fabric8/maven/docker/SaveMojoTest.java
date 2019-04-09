package io.fabric8.maven.docker;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.Logger;
import mockit.Deencapsulation;
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
