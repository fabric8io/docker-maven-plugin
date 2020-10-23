package io.fabric8.maven.docker.util;

import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.Port;

import io.fabric8.maven.docker.UnixOnlyTests;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import mockit.Mocked;
import mockit.Verifications;
import org.apache.maven.plugins.assembly.model.Assembly;
import org.apache.maven.plugins.assembly.model.FileItem;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.fabric8.maven.docker.util.JibServiceUtil.BUSYBOX;
import static io.fabric8.maven.docker.util.JibServiceUtil.containerFromImageConfiguration;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JibServiceUtilTest {
    @Test
    public void testGetBaseImageWithNullBuildConfig() {
        assertEquals(BUSYBOX, JibServiceUtil.getBaseImage(new ImageConfiguration.Builder().build()));
    }

    @Test
    public void testGetBaseImageWithNotNullBuildConfig() {
        assertEquals("quay.io/jkubeio/jkube-test-image:0.0.1", JibServiceUtil.getBaseImage(new ImageConfiguration.Builder()
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("quay.io/jkubeio/jkube-test-image:0.0.1")
                        .build())
                .build()));
    }

    @Test
    public void testContainerFromImageConfiguration(@Mocked JibContainerBuilder containerBuilder) throws Exception {
        // Given
        ImageConfiguration imageConfiguration = getSampleImageConfiguration();
        // When
        JibContainerBuilder jibContainerBuilder = containerFromImageConfiguration(ImageFormat.Docker.name(), imageConfiguration, null);
        // Then
        // @formatter:off
        new Verifications() {{
            jibContainerBuilder.addLabel("foo", "bar");
            times = 1;
            jibContainerBuilder.setEntrypoint(Arrays.asList("java", "-jar", "foo.jar"));
            times = 1;
            jibContainerBuilder.setExposedPorts(new HashSet<>(Collections.singletonList(Port.tcp(8080))));
            times = 1;
            jibContainerBuilder.setUser("root");
            times = 1;
            jibContainerBuilder.setWorkingDirectory(AbsoluteUnixPath.get("/home/foo"));
            times = 1;
            jibContainerBuilder.setVolumes(new HashSet<>(Collections.singletonList(AbsoluteUnixPath.get("/mnt/volume1"))));
            times = 1;
            jibContainerBuilder.setFormat(ImageFormat.Docker);
            times = 1;
        }};
        // @formatter:on
    }

    @Test
    @Category(UnixOnlyTests.class)
    public void testCopyToContainer(@Mocked JibContainerBuilder containerBuilder) throws IOException {
        // Given
        File temporaryDirectory = Files.createTempDirectory("jib-test").toFile();
        File temporaryFile = new File(temporaryDirectory, "foo.txt");
        boolean wasNewFileCreated = temporaryFile.createNewFile();
        String tmpRoot = temporaryDirectory.getParent();

        // When
        JibServiceUtil.copyToContainer(containerBuilder, temporaryDirectory, tmpRoot, Collections.emptyMap());

        // Then
        assertTrue(wasNewFileCreated);
        new Verifications() {{
            FileEntriesLayer fileEntriesLayer;
            containerBuilder.addFileEntriesLayer(fileEntriesLayer = withCapture());

            assertNotNull(fileEntriesLayer);
            assertEquals(1, fileEntriesLayer.getEntries().size());
            assertEquals(temporaryFile.toPath(), fileEntriesLayer.getEntries().get(0).getSourceFile());
            assertEquals(AbsoluteUnixPath.fromPath(Paths.get(temporaryFile.getAbsolutePath().substring(tmpRoot.length()))),
                    fileEntriesLayer.getEntries().get(0).getExtractionPath());
        }};
    }

    @Test
    public void testAppendOriginalImageNameTagIfApplicable() {
        // Given
        List<String> imageTagList = Arrays.asList("0.0.1", "0.0.1-SNAPSHOT");

        // When
        Set<String> result = JibServiceUtil.getAllImageTags(imageTagList, "test-project");

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertArrayEquals(new String[]{"0.0.1-SNAPSHOT", "0.0.1", "latest"}, result.toArray());
    }

    @Test
    public void testGetFullImageNameWithDefaultTag() {
        assertEquals("test/test-project:latest", JibServiceUtil.getFullImageName(getSampleImageConfiguration(), null));
    }

    @Test
    public void testGetFullImageNameWithProvidedTag() {
        assertEquals("test/test-project:0.0.1", JibServiceUtil.getFullImageName(getSampleImageConfiguration(), "0.0.1"));
    }

    @Test
    public void testGetImageFormat() {
        assertEquals(ImageFormat.Docker, JibServiceUtil.getImageFormat("Docker"));
        assertEquals(ImageFormat.OCI, JibServiceUtil.getImageFormat("OCI"));
        assertEquals(ImageFormat.OCI, JibServiceUtil.getImageFormat("oci"));
    }

    private ImageConfiguration getSampleImageConfiguration() {
        Assembly assembly = new Assembly();
        FileItem fileItem = new FileItem();
        fileItem.setSource("${project.basedir}/foo");
        fileItem.setOutputDirectory("/deployments");
        assembly.addFile(fileItem);

        BuildImageConfiguration bc = new BuildImageConfiguration.Builder()
                .from("quay.io/test/testimage:testtag")
                .assembly(new AssemblyConfiguration.Builder()
                        .assemblyDef(assembly)
                        .build())
                .entryPoint(new Arguments.Builder().withParam("java")
                        .withParam("-jar")
                        .withParam("foo.jar")
                        .build())
                .labels(Collections.singletonMap("foo", "bar"))
                .user("root")
                .workdir("/home/foo")
                .ports(Collections.singletonList("8080"))
                .volumes(Collections.singletonList("/mnt/volume1"))
                .build();

        return new ImageConfiguration.Builder()
                .name("test/test-project")
                .buildConfig(bc)
                .build();
    }
}