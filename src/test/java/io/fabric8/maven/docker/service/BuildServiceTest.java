package io.fabric8.maven.docker.service;

import java.io.File;
import java.util.Collections;

import java.util.Properties;


import io.fabric8.maven.docker.access.BuildOptions;
import io.fabric8.maven.docker.access.DockerAccess;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.util.DockerFileUtilTest;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import mockit.Expectations;
import mockit.FullVerifications;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;


public class BuildServiceTest {

    private static final String NEW_IMAGE_ID = "efg789efg789";
    private static final String OLD_IMAGE_ID = "abc123abc123";

    @Tested
    private BuildService buildService;

    @Injectable
    private DockerAccess docker;

    @Mocked
    private DockerAssemblyManager dockerAssemblyManager;

    private ImageConfiguration imageConfig;

    @Injectable
    private Logger log;

    private String oldImageId;

    @Mocked
    private MojoParameters params;

    @Mocked
    Logger logger;

    @Mocked
    MojoParameters mojoParameters;

    @Mocked
    MavenProject mavenProject;

    @Injectable
    private QueryService queryService;

    @Injectable
    private ArchiveService archiveService;

    @Injectable
    private RegistryService registryService;

    @Before
    public void setup() throws Exception {
        new Expectations() {{
            archiveService.createArchive(anyString, (BuildImageConfiguration) any, (MojoParameters) any, log);
            result = new File("docker-build.tar");
        }};
    }

    @Test
    public void testBuildImageWithCleanup() throws Exception {
        givenAnImageConfiguration(true);
        givenImageIds(OLD_IMAGE_ID, NEW_IMAGE_ID);
        whenBuildImage(true,false);
        thenImageIsBuilt();
        thenOldImageIsRemoved();
    }

    @Test
    public void testBuildImageWithNoCleanup() throws Exception {
        givenAnImageConfiguration(false);
        givenImageIds(OLD_IMAGE_ID, NEW_IMAGE_ID);
        whenBuildImage(false,false);
        thenImageIsBuilt();
        thenOldImageIsNotRemoved();
    }

    @Test
    public void testCleanupCachedImage() throws Exception {
        givenAnImageConfiguration(true);
        givenImageIds(OLD_IMAGE_ID, OLD_IMAGE_ID);
        whenBuildImage(false, false);
        thenImageIsBuilt();
        thenOldImageIsNotRemoved();
    }

    @Test
    public void testCleanupNoExistingImage() throws Exception {
        givenAnImageConfiguration(true);
        givenImageIds(null, NEW_IMAGE_ID);
        whenBuildImage(false, false);
        thenImageIsBuilt();
        thenOldImageIsNotRemoved();
    }

    @Test
    public void testMultiStageBuild() throws Exception {
        BuildImageConfiguration buildConfig = new BuildImageConfiguration.Builder()
                .cleanup("false")
                .dockerFile(DockerFileUtilTest.class.getResource("Dockerfile_multi_stage").getPath())
                .filter("false")
                .build();

        buildConfig.initAndValidate(logger);

        imageConfig = new ImageConfiguration.Builder()
                .name("build-image")
                .alias("build-alias")
                .buildConfig(buildConfig)
                .build();

        final ImagePullManager pullManager = new ImagePullManager(null,null, null);
        final BuildService.BuildContext buildContext = new BuildService.BuildContext.Builder()
                .mojoParameters(mojoParameters)
                .build();

        new Expectations(mojoParameters) {{
            mojoParameters.getProject(); result = mavenProject;
            mavenProject.getProperties(); result = new Properties();
        }};

        buildService.buildImage(imageConfig, pullManager, buildContext);

        //verify that tries to pull both images
        new Verifications() {{
            queryService.hasImage("fabric8/s2i-java");
            registryService.pullImageWithPolicy("fabric8/s2i-java",  pullManager, buildContext.getRegistryConfig(), false);
            queryService.hasImage("fabric8/s1i-java");
            registryService.pullImageWithPolicy("fabric8/s1i-java",  pullManager, buildContext.getRegistryConfig(), false);
        }};
    }

    private void givenAnImageConfiguration(Boolean cleanup) {
        BuildImageConfiguration buildConfig = new BuildImageConfiguration.Builder()
                .cleanup(cleanup.toString())
                .build();

        imageConfig = new ImageConfiguration.Builder()
                .name("build-image")
                .alias("build-alias")
                .buildConfig(buildConfig)
                .build();
    }

    private void givenImageIds(final String oldImageId, final String newImageId) throws DockerAccessException {
        this.oldImageId = oldImageId;
        new Expectations() {{
            queryService.getImageId(imageConfig.getName()); result = new String[] { oldImageId, newImageId };
        }};
    }

    private void thenImageIsBuilt() throws DockerAccessException {
        final File dockerBuildTar = new File("docker-build.tar");
        new Verifications() {{
            docker.buildImage(imageConfig.getName(),
                              dockerBuildTar,
                              (BuildOptions) any);
        }};
    }

    private void thenOldImageIsNotRemoved() throws DockerAccessException {
        new FullVerifications(docker) {{

        }};
    }

    private void thenOldImageIsRemoved() throws DockerAccessException {
        new Verifications() {{
            docker.removeImage(oldImageId, true);
        }};
    }

    private void whenBuildImage(boolean cleanup, boolean nocache) throws DockerAccessException, MojoExecutionException {
        new Expectations() {{
            docker.buildImage(withEqual(imageConfig.getName()), (File) any, (BuildOptions) any);
        }};
        if (cleanup) {
            new Expectations() {{
                docker.removeImage(withEqual(oldImageId), withEqual(true)); result = true;
            }};
        }

        buildService.buildImage(imageConfig, params, nocache, Collections.<String, String>emptyMap());

    }
}
