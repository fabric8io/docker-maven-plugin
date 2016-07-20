package io.fabric8.maven.docker.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import io.fabric8.maven.docker.util.ImagePullCache;
import org.apache.maven.plugin.MojoExecutionException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.util.AutoPullMode;
import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.Mocked;

public class QueryServiceAutoPullTest {

    private Exception actualException;

    private boolean alwaysPull;

    private AutoPullMode autoPullMode;

    @Mocked
    private DockerAccess docker;

    private String imageName;

    private boolean imageRequiresPull;

    @Mocked
    private Logger logger;

    private ImagePullCache previousImages;

    private QueryService queryService;

    @Before
    public void setup() {
        previousImages = new ImagePullCache();
        queryService = new QueryService(docker);
    }

    @Test
    public void testPullImageAlways() throws Exception {
        givenAnImage();
        givenPullSettings(AutoPullMode.ALWAYS, true);

        whenCheckIfImageRequiredAutoPull();
        thenImageRequiresPull();

        givenAnImageExists();
        whenCheckIfImageRequiredAutoPull();
        thenImageRequiresPull();
    }

    @Test
    public void testPullImageAlways_forceFalse() throws Exception {
        givenAnImage();
        givenPullSettings(AutoPullMode.ALWAYS, false);
        whenCheckIfImageRequiredAutoPull();
        thenImageRequiresPull();
    }

    @Test
    public void testPullImageOff() throws Exception {
        givenAnImage();
        givenPullSettings(AutoPullMode.OFF, true);
        whenCheckIfImageRequiredAutoPull();
        thenMojoExcecutionExceptionThrown();
    }

    @Test
    public void testPullImageOn() throws Exception {
        givenAnImageDoesNotExist();
        givenPullSettings(AutoPullMode.ON, true);
        whenCheckIfImageRequiredAutoPull();
        thenImageRequiresPull();
    }

    @Test
    public void testPullImageOn_forceFalse() throws Exception {
        givenAnImageDoesNotExist();
        givenPullSettings(AutoPullMode.ON, false);
        whenCheckIfImageRequiredAutoPull();
        thenImageRequiresPull();
    }

    @Test
    public void testPullImageOn_imageExists() throws Exception {
        givenAnImageExists();
        givenPullSettings(AutoPullMode.ON, true);
        whenCheckIfImageRequiredAutoPull();
        thenImageDoesNotRequirePull();
    }

    @Test
    public void testPullImageOnce() throws Exception {
        givenAnImage();
        givenPullSettings(AutoPullMode.ONCE, true);

        whenCheckIfImageRequiredAutoPull();
        thenImageRequiresPull();

        givenPreviousPullHappened();

        givenAnImageExists();
        whenCheckIfImageRequiredAutoPull();
        thenImageDoesNotRequirePull();
    }

    private void givenPreviousPullHappened() {
        previousImages.add(imageName);
    }

    private void givenAnImageDoesNotExist() throws DockerAccessException {
        this.imageName = "test";

        new Expectations() {
            {
                docker.hasImage(imageName);
                returns(false);
            }
        };
    }

    private void givenAnImageExists() throws DockerAccessException {
        this.imageName = "test";

        new Expectations() {
            {
                docker.hasImage(imageName);
                returns(true);
            }
        };
    }

    private void givenAnImage() {
        this.imageName = "test";
    }

    private void givenPullSettings(AutoPullMode mode, boolean alwaysPull) {
        this.autoPullMode = mode;
        this.alwaysPull = alwaysPull;
    }

    private void thenImageDoesNotRequirePull() {
        assertFalse(imageRequiresPull);
    }

    private void thenImageRequiresPull() {
        assertTrue(imageRequiresPull);
    }

    private void thenMojoExcecutionExceptionThrown() {
        assertNotNull(actualException);
        assertTrue(actualException instanceof MojoExecutionException);
    }

    private void whenCheckIfImageRequiredAutoPull() {
        try {
            imageRequiresPull =
                    queryService.imageRequiresAutoPull(autoPullMode.name().toLowerCase(), imageName, alwaysPull, previousImages);
        }
        catch (Exception e) {
            actualException = e;
        }
    }
}
